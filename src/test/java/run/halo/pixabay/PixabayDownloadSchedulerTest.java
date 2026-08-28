package run.halo.pixabay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * Tests for the cron due-check logic of {@link PixabayDownloadScheduler}.
 */
class PixabayDownloadSchedulerTest {

    private static final ZonedDateTime NOW =
        ZonedDateTime.of(2026, 8, 27, 12, 0, 30, 0, ZoneId.of("Asia/Shanghai"));

    private static final String MONTHLY = "0 12 27 * *";

    @Test
    void neverRanBeforeIsDue() {
        // no lastRunAt -> the first scheduled match is due (catch-up/verification run)
        assertTrue(PixabayDownloadScheduler.isDue("", MONTHLY, NOW));
        assertTrue(PixabayDownloadScheduler.isDue(null, MONTHLY, NOW));
    }

    @Test
    void dueExactlyAtSchedule() {
        // ran before the scheduled time -> due
        assertTrue(PixabayDownloadScheduler.isDue("2026-08-27T03:00:00Z", MONTHLY, NOW));
    }

    @Test
    void notDueAfterRun() {
        // already ran at 12:00 -> next match is next month
        assertFalse(PixabayDownloadScheduler.isDue("2026-08-27T04:00:00Z", MONTHLY, NOW));
    }

    @Test
    void firstEnableCountsAsDueBeforeFirstScheduledPoint() {
        // never ran -> catch-up semantics: due immediately for verification
        ZonedDateTime early = ZonedDateTime.of(2026, 8, 1, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
        assertTrue(PixabayDownloadScheduler.isDue("", "0 12 27 * *", early));
    }

    @Test
    void acceptsQuartzStyleSixFieldsWithQuestionMark() {
        // "sec min hour dom month dow" with ? = daily 10:20
        ZonedDateTime at = ZonedDateTime.of(2026, 8, 28, 10, 20, 11, 0, ZoneId.of("Asia/Shanghai"));
        assertTrue(PixabayDownloadScheduler.isDue("", "11 20 10 * * ?", at));
        ZonedDateTime before = ZonedDateTime.of(2026, 8, 28, 10, 21, 0, 0,
            ZoneId.of("Asia/Shanghai"));
        assertFalse(PixabayDownloadScheduler.isDue("2026-08-28T02:20:00Z", "11 20 10 * * ?", before));
    }

    @Test
    void invalidCronNeverDue() {
        assertFalse(PixabayDownloadScheduler.isDue("", "not a cron", NOW));
        assertFalse(PixabayDownloadScheduler.isDue("", "", NOW));
    }

    @Test
    void disabledByBlankCron() {
        assertFalse(PixabayDownloadScheduler.isDue("", "   ", NOW));
    }
}
