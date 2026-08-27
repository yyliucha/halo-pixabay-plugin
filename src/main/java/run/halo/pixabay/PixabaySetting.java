package run.halo.pixabay;

/**
 * Plugin settings POJO, bound to the "basic" settings group.
 *
 * @param apiKey           Pixabay API key
 * @param keywords         comma-separated search keywords
 * @param countPerKeyword  new images to download per keyword per run
 * @param imageSize        original / large / webformat / preview
 * @param imageType        photo / illustration / vector / all
 * @param attachmentPolicy attachment policy name; blank = auto resolve
 * @param attachmentGroup  attachment group name; blank = default group
 * @param enabled          whether the scheduled download is enabled
 * @param cron             cron expression, e.g. "0 12 27 * *"
 */
public record PixabaySetting(
    String apiKey,
    String keywords,
    Integer countPerKeyword,
    String imageSize,
    String imageType,
    String attachmentPolicy,
    String attachmentGroup,
    Boolean enabled,
    String cron
) {

    public PixabaySetting {
        if (keywords == null || keywords.isBlank()) {
            keywords = "mountain,landscape,forest";
        }
        if (countPerKeyword == null || countPerKeyword < 1) {
            countPerKeyword = 50;
        }
        if (imageSize == null || imageSize.isBlank()) {
            imageSize = "original";
        }
        if (imageType == null || imageType.isBlank()) {
            imageType = "photo";
        }
        if (enabled == null) {
            enabled = false;
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 12 27 * *";
        }
    }

    public boolean scheduledEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
