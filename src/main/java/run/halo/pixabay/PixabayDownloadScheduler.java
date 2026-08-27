package run.halo.pixabay;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Scheduled download trigger.
 *
 * <p>Ticks every minute and checks whether the configured cron expression is
 * due since the last run. First-time enable (no run record yet) counts as due,
 * so the first batch downloads ~1 minute after enabling - a built-in catch-up
 * and verification run. After that, downloads happen on the cron schedule.</p>
 */
@Slf4j
@Component
public class PixabayDownloadScheduler {

    private final PixabayDownloadService downloadService;
    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient extensionClient;

    public PixabayDownloadScheduler(PixabayDownloadService downloadService,
        ReactiveSettingFetcher settingFetcher, ReactiveExtensionClient extensionClient) {
        this.downloadService = downloadService;
        this.settingFetcher = settingFetcher;
        this.extensionClient = extensionClient;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void tick() {
        settingFetcher.fetch("basic", PixabaySetting.class)
            .filter(PixabaySetting::scheduledEnabled)
            .flatMap(settings -> extensionClient
                .fetch(PixabayDownloadRecord.class, PixabayDownloadRecord.RECORD_NAME)
                .map(record -> record.getSpec().getLastRunAt())
                .defaultIfEmpty("")
                .map(lastRunAt -> isDue(lastRunAt, settings.cron(), ZonedDateTime.now()))
                .filter(Boolean::booleanValue)
                .flatMap(due -> {
                    // Scheduled runs have no request thread to borrow an
                    // authenticated SecurityContext from, so use a synthetic
                    // identity (required by Halo's attachment upload API).
                    downloadService.triggerAsync(false, scheduledSecurityContext());
                    return Mono.empty();
                }))
            .onErrorResume(e -> {
                log.warn("[pixabay] scheduled tick failed: {}", e.getMessage());
                return Mono.empty();
            })
            .subscribe();
    }

    private static SecurityContext scheduledSecurityContext() {
        var authentication = new UsernamePasswordAuthenticationToken(
            "pixabay-downloader", "", List.of());
        return new SecurityContextImpl(authentication);
    }

    /**
     * Max minutes to scan backwards when looking for a due cron match
     * (one year). Bounds the never-ran catch-up scan.
     */
    private static final long MAX_SCAN_MINUTES = 525_600;

    /**
     * Whether the cron expression is due, given the last run time.
     * Returns true when there is a cron match after the last run (and before
     * or at now). Never-ran counts as due (first enable triggers an immediate
     * verification run, then the cron schedule takes over).
     */
    static boolean isDue(String lastRunAt, String cron, ZonedDateTime now) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            CronMatcher matcher = CronMatcher.parse(cron);
            ZonedDateTime last = parseLastRun(lastRunAt);
            ZonedDateTime t = now.truncatedTo(ChronoUnit.MINUTES);
            long scanned = 0;
            while (t.isAfter(last) && scanned < MAX_SCAN_MINUTES) {
                if (matcher.matches(t)) {
                    return true;
                }
                t = t.minusMinutes(1);
                scanned++;
            }
            return false;
        } catch (Exception e) {
            log.warn("[pixabay] invalid cron expression '{}': {}", cron, e.getMessage());
            return false;
        }
    }

    private static ZonedDateTime parseLastRun(String lastRunAt) {
        if (lastRunAt == null || lastRunAt.isBlank()) {
            // never ran before -> the first match of the expression is due
            return Instant.EPOCH.atZone(java.time.ZoneOffset.UTC);
        }
        try {
            return Instant.parse(lastRunAt).atZone(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return Instant.EPOCH.atZone(java.time.ZoneOffset.UTC);
        }
    }
}
