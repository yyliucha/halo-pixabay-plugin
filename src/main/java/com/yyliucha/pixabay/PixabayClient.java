package com.yyliucha.pixabay;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Reactive client for the official Pixabay API.
 */
@Slf4j
@Component
public class PixabayClient {

    private static final String DEFAULT_BASE_URL = "https://pixabay.com/api/";

    private static final String USER_AGENT =
        "Mozilla/5.0 (compatible; HaloPixabayPlugin/1.0)";

    private final WebClient webClient;

    public PixabayClient() {
        this(WebClient.builder(), DEFAULT_BASE_URL);
    }

    PixabayClient(WebClient.Builder builder, String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl)
            .defaultHeader("User-Agent", USER_AGENT)
            .build();
    }

    /**
     * Download image bytes from an absolute URL. Error responses (e.g. 403/429)
     * are drained via {@code releaseBody()} so the pooled connection is NOT left
     * in a cancelled state (Halo's own uploadFromUrl leaks such connections,
     * poisoning subsequent downloads with
     * "Rejecting additional inbound receiver").
     *
     * @param url absolute image URL
     * @return image bytes
     */
    public Mono<byte[]> download(String url) {
        return webClient.get()
            .uri(url)
            .exchangeToMono(response -> {
                if (response.statusCode().is2xxSuccessful()) {
                    return response.bodyToMono(byte[].class);
                }
                return response.releaseBody()
                    .then(Mono.error(new IllegalStateException(
                        "HTTP " + response.statusCode().value())));
            });
    }

    /**
     * Search images for one keyword page.
     */
    public Mono<PixabaySearchResponse> search(String apiKey, String query, int page,
        int perPage, String imageType, boolean safeSearch) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .queryParam("key", apiKey)
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .queryParam("image_type", imageType)
                .queryParam("safesearch", safeSearch ? 1 : 0)
                .build())
            .retrieve()
            .bodyToMono(PixabaySearchResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(16))
                .filter(throwable -> true));
    }
}
