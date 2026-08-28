package com.yyliucha.pixabay;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Group;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Core service: orchestrates keyword search -> dedupe -> upload to attachment
 * storage -> record history. Download rules are the same as the Python version:
 * official API, pagination until the target count of NEW images is reached,
 * global dedupe by Pixabay id, automatic retries, size tiers.
 */
@Slf4j
@Component
public class PixabayDownloadService {

    private static final int MAX_PAGES = 50;

    private final PixabayClient pixabayClient;
    private final AttachmentService attachmentService;
    private final ReactiveExtensionClient extensionClient;
    private final ReactiveSettingFetcher settingFetcher;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> firstUploadError = new AtomicReference<>();

    public PixabayDownloadService(PixabayClient pixabayClient,
        AttachmentService attachmentService,
        ReactiveExtensionClient extensionClient,
        ReactiveSettingFetcher settingFetcher) {
        this.pixabayClient = pixabayClient;
        this.attachmentService = attachmentService;
        this.extensionClient = extensionClient;
        this.settingFetcher = settingFetcher;
    }

    /**
     * Run one download pass. Concurrent invocations are rejected with a message.
     *
     * @param manual true when triggered from the console UI, false when scheduled
     */
    public Mono<DownloadSummary> runOnce(boolean manual) {
        if (!running.compareAndSet(false, true)) {
            return Mono.just(DownloadSummary.skipped("Another download is already running"));
        }
        return doRun(manual)
            .onErrorResume(e -> {
                log.error("Pixabay download failed", e);
                String message = describeError(e);
                return recordFailure(message).thenReturn(DownloadSummary.failed(message));
            })
            .doFinally(signal -> running.set(false));
    }

    /**
     * Best-effort persistence of a run-level failure into the download record,
     * so the console page shows the failure reason even when it happens before
     * any image is processed.
     */
    private Mono<Void> recordFailure(String message) {
        return Mono.defer(() -> loadRecord()
                .flatMap(record -> {
                    record.getSpec().setLastRunAt(Instant.now().toString());
                    record.getSpec().setLastRunMessage("failed: " + message);
                    record.getSpec().setLastRunError(message);
                    return saveRecord(record)
                        .onErrorResume(err -> {
                            log.warn("[pixabay] failed to persist run failure record", err);
                            return Mono.empty();
                        });
                }))
            .onErrorResume(err -> Mono.empty());
    }

    private static String describeError(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        String lastMessage = null;
        while (current != null && !current.equals(lastMessage)) {
            lastMessage = current.getMessage();
            if (current == e) {
                sb.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
            } else if (current.getMessage() != null && !current.getMessage().isBlank()) {
                sb.append(" <- ").append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /**
     * Trigger a download run in the background and return immediately so the
     * console request never hits a gateway timeout (a run can take minutes).
     * Progress is observed through the download record by polling.
     *
     * <p>The Halo attachment upload API requires an authenticated
     * {@link SecurityContext} on the executing thread (it fails with
     * {@code 401 UNAUTHORIZED "Authentication required."} otherwise), so the
     * context captured from the triggering request must be propagated into the
     * background subscription.</p>
     */
    public void triggerAsync(boolean manual, SecurityContext securityContext) {
        runOnce(manual)
            // Spring Security 6.5+/7.x: the reactive holder keys the context with
            // the SecurityContext CLASS and stores a Mono<SecurityContext> value.
            // (Putting the class-name string would hit the servlet ThreadLocal
            // accessor expecting a raw SecurityContext and break context
            // propagation -> "Failed to obtain R2DBC Connection".)
            .contextWrite(ctx -> ctx.put(SecurityContext.class, Mono.just(securityContext)))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                summary -> log.info(
                    "[pixabay] background download finished: added={}, failed={}, total={}, "
                        + "message={}",
                    summary.added(), summary.failed(), summary.total(), summary.message()),
                error -> log.error("[pixabay] background download failed unexpectedly", error)
            );
    }

    private Mono<DownloadSummary> doRun(boolean manual) {
        firstUploadError.set(null);
        return settingFetcher.fetch("basic", PixabaySetting.class)
            .switchIfEmpty(Mono.error(
                new IllegalStateException("Plugin settings are not configured yet")))
            .flatMap(settings -> {
                if (!manual && !settings.scheduledEnabled()) {
                    return Mono.just(DownloadSummary.skipped("Scheduled download is disabled"));
                }
                if (settings.apiKey() == null || settings.apiKey().isBlank()) {
                    return Mono.error(
                        new IllegalArgumentException("Pixabay API key is not configured"));
                }
                return runDownload(settings);
            });
    }

    private Mono<DownloadSummary> runDownload(PixabaySetting settings) {
        String[] keywords = Arrays.stream(settings.keywords().split(","))
            .map(String::trim)
            .filter(k -> !k.isEmpty())
            .toArray(String[]::new);
        if (keywords.length == 0) {
            return Mono.just(DownloadSummary.failed("No keywords configured"));
        }

        List<GroupRun> groups = groupKeywords(settings, keywords);
        return loadRecord()
            .flatMap(record -> Flux.fromIterable(groups)
                .concatMap(group -> runGroup(group, settings, record))
                .collectList()
                .flatMap(results -> {
                    int added = results.stream().mapToInt(r -> r.added).sum();
                    int failed = results.stream().mapToInt(r -> r.failed).sum();
                    int total = results.stream().mapToInt(r -> r.total).sum();
                    String message = results.stream()
                        .map(r -> r.message)
                        .collect(java.util.stream.Collectors.joining("；"));
                    String firstError = firstUploadError.get();
                    if (failed > 0 && firstError != null) {
                        message = message + "；示例错误: " + firstError;
                    }
                    record.getSpec().setLastRunAt(Instant.now().toString());
                    record.getSpec().setLastRunMessage(message);
                    record.getSpec().setLastRunAdded(added);
                    record.getSpec().setLastRunFailed(failed);
                    record.getSpec().setLastRunTotal(total);
                    record.getSpec().setLastRunError(firstError);
                    return saveRecord(record).thenReturn(
                        DownloadSummary.of(added, 0, failed, total, message));
                }));
    }

    /**
     * Group keywords by their configured mapping: keywords sharing the same
     * policy/group mapping are downloaded together; unmapped keywords fall back
     * to the global policy/group.
     */
    private static List<GroupRun> groupKeywords(PixabaySetting settings, String[] keywords) {
        Map<String, PixabaySetting.KeywordMapping> byKeyword = new LinkedHashMap<>();
        for (PixabaySetting.KeywordMapping mapping : settings.keywordMappings()) {
            if (mapping != null && mapping.keyword() != null && !mapping.keyword().isBlank()) {
                byKeyword.putIfAbsent(mapping.keyword().trim(), mapping);
            }
        }
        Map<PixabaySetting.KeywordMapping, List<String>> groups = new LinkedHashMap<>();
        for (String keyword : keywords) {
            PixabaySetting.KeywordMapping mapping = byKeyword.get(keyword);
            groups.computeIfAbsent(mapping, k -> new ArrayList<>()).add(keyword);
        }
        List<GroupRun> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            result.add(new GroupRun(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Mono<GroupResult> runGroup(GroupRun group, PixabaySetting settings,
        PixabayDownloadRecord record) {
        var mapping = group.mapping();
        String policyRef = (mapping != null && mapping.policy() != null
            && !mapping.policy().isBlank()) ? mapping.policy().trim() : settings.attachmentPolicy();
        String groupRef = (mapping != null && mapping.group() != null
            && !mapping.group().isBlank()) ? mapping.group().trim() : settings.attachmentGroup();
        return resolvePolicy(policyRef)
            .flatMap(policy -> resolveGroup(groupRef)
                .flatMap(groupName -> {
                    String grp = (groupName == null || groupName.isBlank()) ? null : groupName;
                    return Flux.fromIterable(group.keywords())
                        .concatMap(keyword -> downloadKeyword(settings, record, policy, grp, keyword))
                        .collectList()
                        .map(results -> {
                            int added = results.stream().mapToInt(r -> r.added).sum();
                            int failed = results.stream().mapToInt(r -> r.failed).sum();
                            int total = results.stream().mapToInt(r -> r.total).sum();
                            String label = mapping == null ? "全局"
                                : mapping.keyword().trim() + "→"
                                    + (policyRef.isBlank() ? "全局策略" : policyRef);
                            return new GroupResult(
                                label + ": added " + added + ", failed " + failed,
                                added, failed, total);
                        });
                }));
    }

    record GroupRun(PixabaySetting.KeywordMapping mapping, List<String> keywords) {
    }

    record GroupResult(String message, int added, int failed, int total) {
    }

    /**
     * Download new images for one keyword: paginate until the target count of
     * NEW images is collected or results are exhausted.
     */
    private Mono<KeywordResult> downloadKeyword(PixabaySetting settings,
        PixabayDownloadRecord record, String policy, String groupName, String keyword) {
        Set<String> history = record.getSpec().getDownloadedIds();
        int target = settings.countPerKeyword();
        int perPage = Math.min(200, Math.max(3, target));

        AtomicInteger added = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        return fetchNewImages(settings, history, keyword, 1, perPage, target,
            new ArrayList<>(), new ArrayList<>())
            .flatMap(images -> {
                if (images.isEmpty()) {
                    log.info("[pixabay] keyword '{}' has no new images", keyword);
                    return Mono.just(new KeywordResult(0, 0, 0));
                }
                return Flux.fromIterable(images)
                    .concatMap(image -> uploadOne(settings, history, policy, groupName, image))
                    .doOnNext(ok -> {
                        if (ok) {
                            added.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    })
                    .then(Mono.fromSupplier(
                        () -> new KeywordResult(added.get(), failed.get(), added.get() + failed.get())));
            });
    }

    private Mono<List<PixabayImage>> fetchNewImages(PixabaySetting settings,
        Set<String> history, String keyword, int page, int perPage, int target,
        List<PixabayImage> acc, List<Long> accIds) {
        if (acc.size() >= target || page > MAX_PAGES) {
            return Mono.just(acc);
        }
        return pixabayClient.search(settings.apiKey(), keyword, page, perPage,
            settings.imageType(), true)
            .flatMap(response -> {
                List<PixabayImage> hits = response.hits() == null ? List.of() : response.hits();
                for (PixabayImage hit : hits) {
                    if (acc.size() >= target) {
                        break;
                    }
                    if (history.contains(String.valueOf(hit.id())) || accIds.contains(hit.id())) {
                        continue;
                    }
                    acc.add(hit);
                    accIds.add(hit.id());
                }
                boolean morePages = !hits.isEmpty() && hits.size() >= perPage
                    && acc.size() < target;
                if (!morePages) {
                    return Mono.just(acc);
                }
                return fetchNewImages(settings, history, keyword, page + 1, perPage, target, acc,
                    accIds);
            });
    }

    private Mono<Boolean> uploadOne(PixabaySetting settings, Set<String> history,
        String policy, String groupName, PixabayImage image) {
        List<String> urls = urlCandidates(image, settings.imageSize());
        if (urls.isEmpty()) {
            log.warn("[pixabay] image {} has no usable URL, skipped", image.id());
            return Mono.just(false);
        }
        return tryUpload(urls, 0, false, new AtomicReference<>(), history, policy, groupName,
            image);
    }

    /**
     * Try uploading each candidate URL in order. A failed attempt is retried
     * once (transient network/pool issues), then the next tier is tried. The
     * public CDN URL derived from the preview link is preferred, since the
     * {@code pixabay.com/get/...} links are anonymously rate-limited (429).
     */
    private Mono<Boolean> tryUpload(List<String> urls, int index, boolean retried,
        AtomicReference<String> lastError, Set<String> history, String policy, String groupName,
        PixabayImage image) {
        if (index >= urls.size()) {
            String error = lastError.get();
            if (error != null) {
                firstUploadError.compareAndSet(null, error);
            }
            return Mono.just(false);
        }
        String url = urls.get(index);
        String filename = image.id() + guessExt(url);
        return uploadOnce(url, filename, lastError, history, policy, groupName, image)
            .flatMap(ok -> {
                if (ok) {
                    return Mono.just(true);
                }
                if (!retried) {
                    log.warn("[pixabay] retrying image {} via {}", image.id(), url);
                    return tryUpload(urls, index, true, lastError, history, policy, groupName,
                        image);
                }
                return tryUpload(urls, index + 1, false, lastError, history, policy, groupName,
                    image);
            });
    }

    private Mono<Boolean> uploadOnce(String url, String filename,
        AtomicReference<String> lastError, Set<String> history, String policy, String groupName,
        PixabayImage image) {
        // Download with our own client (clean error handling, no pooled-connection
        // poisoning) then upload the bytes via the authenticated upload API.
        return pixabayClient.download(url)
            .timeout(Duration.ofSeconds(30))
            .flatMap(bytes -> attachmentService.upload(policy, groupName, filename,
                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)),
                guessMediaType(filename)))
            .map(attachment -> {
                history.add(String.valueOf(image.id()));
                return true;
            })
            .onErrorResume(e -> {
                log.warn("[pixabay] upload failed for image {} via {}: {}", image.id(), url,
                    e.getMessage());
                lastError.set(e.getMessage() + " (URL: " + url + ")");
                return Mono.just(false);
            });
    }

    /**
     * Derive the media type from the filename extension so attachments are
     * recognized as images by Halo (preview + thumbnail generation require the
     * media type to be set).
     */
    private static MediaType guessMediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        if (lower.endsWith(".bmp")) {
            return MediaType.valueOf("image/bmp");
        }
        if (lower.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        return MediaType.IMAGE_JPEG;
    }

    /**
     * Candidate image URLs for a size tier, in fallback order. CDN-derived
     * URLs (from the preview link path) come first because the API's
     * {@code pixabay.com/get/...} links are anonymous rate-limited (429).
     */
    private static List<String> urlCandidates(PixabayImage image, String size) {
        String cdnOriginal = image.cdnUrl(".jpg");
        String cdnLarge = image.cdnUrl("_1280.jpg");
        String cdnWebformat = image.cdnUrl("_640.jpg");
        List<String> fields = switch (size == null ? "original" : size) {
            case "large" -> List.of("cdnLarge", "largeImageURL", "cdnOriginal", "imageURL",
                "cdnWebformat", "webformatURL", "previewURL");
            case "webformat" -> List.of("cdnWebformat", "webformatURL", "previewURL");
            case "preview" -> List.of("previewURL");
            default -> List.of("cdnOriginal", "imageURL", "cdnLarge", "largeImageURL",
                "cdnWebformat", "webformatURL", "previewURL");
        };
        List<String> urls = new ArrayList<>();
        for (String field : fields) {
            String url = switch (field) {
                case "cdnOriginal" -> cdnOriginal;
                case "cdnLarge" -> cdnLarge;
                case "cdnWebformat" -> cdnWebformat;
                case "imageURL" -> image.imageURL();
                case "largeImageURL" -> image.largeImageURL();
                case "webformatURL" -> image.webformatURL();
                default -> image.previewURL();
            };
            if (url != null && !url.isBlank() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private Mono<PixabayDownloadRecord> loadRecord() {
        return extensionClient.fetch(PixabayDownloadRecord.class, PixabayDownloadRecord.RECORD_NAME)
            .switchIfEmpty(Mono.defer(() -> {
                var record = new PixabayDownloadRecord();
                record.setMetadata(new run.halo.app.extension.Metadata());
                record.getMetadata().setName(PixabayDownloadRecord.RECORD_NAME);
                return Mono.just(record);
            }));
    }

    private Mono<Void> saveRecord(PixabayDownloadRecord record) {
        if (record.getMetadata().getCreationTimestamp() == null) {
            return extensionClient.create(record).then();
        }
        return extensionClient.update(record).then();
    }

    private Mono<String> resolvePolicy(String policyRef) {
        if (policyRef != null && !policyRef.isBlank()) {
            return Mono.just(policyRef.trim());
        }
        return extensionClient.list(Policy.class, null, null, 0, 10)
            .map(result -> result.getItems().stream()
                .map(policy -> policy.getMetadata().getName())
                .toList())
            .flatMap(names -> {
                if (names.size() == 1) {
                    return Mono.just(names.get(0));
                }
                if (names.isEmpty()) {
                    return Mono.error(new ServerErrorException(
                        "No attachment policy found. Please configure one first.",
                        new IllegalStateException("no policy")));
                }
                return Mono.error(new ServerErrorException(
                    "Multiple attachment policies found: " + names
                        + ". Please specify 'attachmentPolicy' in plugin settings.",
                    new IllegalStateException("multiple policies")));
            });
    }

    private Mono<String> resolveGroup(String groupRef) {
        if (groupRef == null || groupRef.isBlank()) {
            return Mono.just("");
        }
        String groupName = groupRef.trim();
        return extensionClient.fetch(Group.class, groupName)
            .switchIfEmpty(Mono.defer(() -> {
                var newGroup = new Group();
                newGroup.setMetadata(new run.halo.app.extension.Metadata());
                newGroup.getMetadata().setName(groupName);
                newGroup.setSpec(new Group.GroupSpec());
                newGroup.getSpec().setDisplayName(groupName);
                return extensionClient.create(newGroup).then(Mono.empty());
            }))
            .thenReturn(groupName);
    }

    /**
     * Download one fresh image for the given keyword and return its permalink
     * URL (used by the article auto-cover feature). The image is marked as
     * downloaded so later batch runs keep the global dedupe.
     */
    public Mono<String> downloadForCover(String keyword) {
        return settingFetcher.fetch("basic", PixabaySetting.class)
            .switchIfEmpty(Mono.error(
                new IllegalStateException("Plugin settings are not configured yet")))
            .flatMap(settings -> {
                if (settings.apiKey() == null || settings.apiKey().isBlank()) {
                    return Mono.error(
                        new IllegalArgumentException("Pixabay API key is not configured"));
                }
                final String policyRef = settings.attachmentPolicy();
                final String groupRef = settings.attachmentGroup();
                return resolvePolicy(policyRef)
                    .flatMap(policy -> resolveGroup(groupRef)
                        .flatMap(groupName -> {
                            String grp = (groupName == null || groupName.isBlank())
                                ? null : groupName;
                            return pixabayClient.search(settings.apiKey(), keyword, 1, 5,
                                    settings.imageType(), true)
                                .flatMap(response -> pickCoverImage(response, settings))
                                .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "No usable image found for keyword: " + keyword)))
                                .flatMap(image -> attemptAttachmentDownload(
                                    urlCandidates(image, settings.imageSize()), 0, image, policy,
                                    grp)
                                    .flatMap(attachment -> markDownloaded(image.id())
                                        .thenReturn(attachment)))
                                .flatMap(attachment ->
                                    attachmentService.getPermalink(attachment)
                                        .map(URI::toString));
                        }));
            });
    }

    private static Mono<PixabayImage> pickCoverImage(PixabaySearchResponse response,
        PixabaySetting settings) {
        List<PixabayImage> hits = response.hits() == null ? List.of() : response.hits();
        for (PixabayImage hit : hits) {
            if (!urlCandidates(hit, settings.imageSize()).isEmpty()) {
                return Mono.just(hit);
            }
        }
        return Mono.empty();
    }

    private Mono<Attachment> attemptAttachmentDownload(List<String> urls, int index,
        PixabayImage image, String policy, String groupName) {
        if (index >= urls.size()) {
            return Mono.error(new IllegalStateException("Failed to download image " + image.id()));
        }
        String url = urls.get(index);
        String filename = image.id() + guessExt(url);
        return pixabayClient.download(url)
            .timeout(Duration.ofSeconds(30))
            .flatMap(bytes -> attachmentService.upload(policy, groupName, filename,
                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)),
                guessMediaType(filename)))
            .onErrorResume(e -> {
                log.warn("[pixabay] cover download failed for image {} via {}: {}", image.id(),
                    url, e.getMessage());
                return attemptAttachmentDownload(urls, index + 1, image, policy, groupName);
            });
    }

    /**
     * Best-effort: record that the image id is downloaded (global dedupe).
     */
    private Mono<Void> markDownloaded(long imageId) {
        return loadRecord()
            .flatMap(record -> {
                record.getSpec().getDownloadedIds().add(String.valueOf(imageId));
                return saveRecord(record);
            })
            .onErrorResume(e -> {
                log.warn("[pixabay] failed to mark image {} downloaded", imageId, e);
                return Mono.empty();
            });
    }

    private static String guessExt(String url) {
        try {
            String path = new URI(url).getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && path.length() - dot <= 6) {
                String ext = path.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]+")) {
                    return ext;
                }
            }
        } catch (URISyntaxException ignored) {
            // fall through
        }
        return ".jpg";
    }

    record KeywordResult(int added, int failed, int total) {
    }
}
