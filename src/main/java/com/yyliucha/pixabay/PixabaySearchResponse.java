package com.yyliucha.pixabay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Search response of the Pixabay API.
 *
 * @param total     total matches
 * @param totalHits capped total matches (max 500)
 * @param hits      image hits of the current page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PixabaySearchResponse(
    long total,
    long totalHits,
    List<PixabayImage> hits
) {
}
