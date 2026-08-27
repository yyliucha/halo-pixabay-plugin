package run.halo.pixabay;

/**
 * Result of one download run, returned to the console UI.
 *
 * @param added     new attachments created
 * @param skipped   images skipped (already downloaded)
 * @param failed    failed uploads
 * @param total     images processed
 * @param runAt     run time (ISO-8601)
 * @param message   human-readable summary
 */
public record DownloadSummary(
    int added,
    int skipped,
    int failed,
    int total,
    String runAt,
    String message
) {

    public static DownloadSummary of(int added, int skipped, int failed, int total, String message) {
        return new DownloadSummary(added, skipped, failed, total, java.time.Instant.now().toString(),
            message);
    }

    public static DownloadSummary failed(String message) {
        return of(0, 0, 0, 0, message);
    }

    public static DownloadSummary skipped(String message) {
        return of(0, 0, 0, 0, message);
    }
}
