package run.halo.pixabay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single image hit from the Pixabay API response.
 *
 * @param id          Pixabay image id
 * @param tags        image tags
 * @param pageURL     page URL on pixabay.com
 * @param type        photo / illustration / vector
 * @param previewURL  150px thumbnail
 * @param webformatURL 640px webformat
 * @param largeImageURL ~1280px large
 * @param imageURL    original image
 * @param user        uploader name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PixabayImage(
    long id,
    String tags,
    @JsonProperty("pageURL") String pageURL,
    String type,
    @JsonProperty("previewURL") String previewURL,
    @JsonProperty("webformatURL") String webformatURL,
    @JsonProperty("largeImageURL") String largeImageURL,
    @JsonProperty("imageURL") String imageURL,
    String user
) {

    /**
     * Pick the URL for the requested size tier, falling back to smaller tiers
     * when the preferred field is missing (same rules as the Python version).
     */
    public String pickUrl(String size) {
        return switch (size == null ? "original" : size) {
            case "large" -> firstNonNull(largeImageURL, imageURL, webformatURL, previewURL);
            case "webformat" -> firstNonNull(webformatURL, previewURL);
            case "preview" -> previewURL;
            default -> firstNonNull(imageURL, largeImageURL, webformatURL, previewURL);
        };
    }

    /**
     * Derive a URL for another size on the public CDN
     * ({@code cdn.pixabay.com}), based on the preview URL's path. The
     * {@code pixabay.com/get/...} links returned by the API are anonymous
     * rate-limited (HTTP 429), so downloading from the CDN path is far more
     * reliable. Returns {@code null} when the preview URL is unavailable.
     *
     * @param suffix e.g. {@code "_640.jpg"}, {@code "_1280.jpg"} or {@code ".jpg"}
     */
    public String cdnUrl(String suffix) {
        if (previewURL == null || previewURL.isBlank()) {
            return null;
        }
        return previewURL.replaceFirst("_\\d+\\.jpg$", suffix);
    }

    private static String firstNonNull(String... urls) {
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }
}
