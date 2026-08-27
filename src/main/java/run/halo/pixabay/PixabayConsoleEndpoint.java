package run.halo.pixabay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Console REST endpoints:
 * <ul>
 *   <li>POST /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/download
 *   - trigger a manual download run</li>
 *   <li>GET /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/record
 *   - fetch the dedupe/download history record</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PixabayConsoleEndpoint implements CustomEndpoint {

    private final PixabayDownloadService downloadService;
    private final ReactiveExtensionClient extensionClient;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("/plugins/pixabay-downloader/record", this::getRecord,
                builder -> builder.operationId("GetPixabayDownloadRecord")
                    .tag("pixabay-downloader"))
            .POST("/plugins/pixabay-downloader/download", this::download,
                builder -> builder.operationId("TriggerPixabayDownload")
                    .tag("pixabay-downloader"))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.pixabay.halo.run", "v1alpha1");
    }

    private Mono<ServerResponse> getRecord(ServerRequest request) {
        return extensionClient.fetch(PixabayDownloadRecord.class, PixabayDownloadRecord.RECORD_NAME)
            .flatMap(record -> ServerResponse.ok().bodyValue(record))
            .switchIfEmpty(Mono.defer(
                () -> ServerResponse.ok().bodyValue(new PixabayDownloadRecord())));
    }

    private Mono<ServerResponse> download(ServerRequest request) {
        return downloadService.runOnce(true)
            .flatMap(summary -> ServerResponse.ok().bodyValue(summary));
    }
}
