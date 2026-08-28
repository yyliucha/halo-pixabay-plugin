package com.yyliucha.pixabay;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.content.Post;
import run.halo.app.event.post.PostPublishedEvent;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Article auto-cover feature: when a post is published without a cover, pick a
 * fresh Pixabay image matching the post tags/title, download it into the
 * attachment library and set it as the post cover. Runs in the background so it
 * never blocks the reconciler; the synthetic identity satisfies the attachment
 * upload authentication requirement.
 */
@Slf4j
@Component
public class ArticleAutoCover {

    private static final int MAX_TAGS = 3;

    private final PixabayDownloadService downloadService;
    private final ReactiveExtensionClient extensionClient;
    private final ReactiveSettingFetcher settingFetcher;

    public ArticleAutoCover(PixabayDownloadService downloadService,
        ReactiveExtensionClient extensionClient, ReactiveSettingFetcher settingFetcher) {
        this.downloadService = downloadService;
        this.extensionClient = extensionClient;
        this.settingFetcher = settingFetcher;
    }

    @EventListener
    public void onPostPublished(PostPublishedEvent event) {
        handle(event)
            .contextWrite(ctx -> ctx.put(SecurityContext.class,
                Mono.just(SystemIdentity.securityContext())))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> log.info("[pixabay] auto cover applied for post {}",
                    event.getName()),
                error -> log.warn("[pixabay] auto cover failed for post {}: {}",
                    event.getName(), error.getMessage()));
    }

    /**
     * The auto-cover pipeline, exposed for tests.
     */
    Mono<Void> handle(PostPublishedEvent event) {
        return settingFetcher.fetch("basic", PixabaySetting.class)
            .filter(settings -> Boolean.TRUE.equals(settings.articleCoverEnabled()))
            .flatMap(settings -> extensionClient.fetch(Post.class, event.getName()))
            .filter(post -> post.getSpec() != null && isBlank(post.getSpec().getCover()))
            .flatMap(post -> {
                String keyword = coverKeyword(post);
                if (keyword == null) {
                    log.debug("[pixabay] post {} has no usable keyword, skip auto cover",
                        event.getName());
                    return Mono.empty();
                }
                return downloadService.downloadForCover(keyword)
                    .flatMap(permalink -> {
                        post.getSpec().setCover(permalink);
                        return extensionClient.update(post).then();
                    });
            });
    }

    private static String coverKeyword(Post post) {
        var spec = post.getSpec();
        if (spec == null) {
            return null;
        }
        List<String> tags = spec.getTags() == null ? List.of() : spec.getTags().stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
            .toList();
        if (!tags.isEmpty()) {
            return String.join(" ", tags.subList(0, Math.min(MAX_TAGS, tags.size())));
        }
        if (spec.getTitle() != null && !spec.getTitle().isBlank()) {
            return spec.getTitle().trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
