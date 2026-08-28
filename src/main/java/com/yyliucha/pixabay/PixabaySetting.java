package com.yyliucha.pixabay;

import java.util.List;

/**
 * Plugin settings POJO, bound to the "basic" settings group.
 *
 * @param apiKey             Pixabay API key
 * @param keywords           comma-separated search keywords
 * @param countPerKeyword    new images to download per keyword per run
 * @param imageSize          original / large / webformat / preview
 * @param imageType          photo / illustration / vector / all
 * @param attachmentPolicy   global attachment policy name; blank = auto resolve
 * @param attachmentGroup    global attachment group name; blank = default group
 * @param enabled            whether the scheduled download is enabled
 * @param cron               cron expression, e.g. "0 12 27 * *"
 * @param articleCoverEnabled whether published posts get an auto cover image
 * @param keywordMappings    per-keyword policy/group mapping (fallback: global)
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
    String cron,
    Boolean articleCoverEnabled,
    List<KeywordMapping> keywordMappings
) {

    /**
     * One-to-one mapping of a keyword to its attachment policy and group.
     * Blank policy/group means "inherit the global values".
     */
    public record KeywordMapping(String keyword, String policy, String group) {
    }

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
        if (articleCoverEnabled == null) {
            articleCoverEnabled = false;
        }
        if (keywordMappings == null) {
            keywordMappings = List.of();
        }
    }

    public boolean scheduledEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
