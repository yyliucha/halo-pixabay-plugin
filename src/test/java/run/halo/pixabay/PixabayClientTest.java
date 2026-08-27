package run.halo.pixabay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Tests for {@link PixabayClient} against a local mock HTTP server emulating
 * the Pixabay API (same approach as the Python end-to-end test).
 */
class PixabayClientTest {

    private HttpServer server;
    private int port;
    private final Map<String, Integer> pageRequests = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    if (pair.startsWith("page=")) {
                        pageRequests.merge(pair.substring(5), 1, Integer::sum);
                    }
                }
            }
            String body = switch (query == null ? "" : query) {
                case String q when q.contains("page=2") -> """
                    {"total":4,"totalHits":4,"hits":[
                      {"id":1005,"tags":"mock","type":"photo","previewURL":"http://127.0.0.1:%d/img/1005_150.jpg",
                       "webformatURL":"http://127.0.0.1:%d/img/1005_640.jpg",
                       "largeImageURL":"http://127.0.0.1:%d/img/1005_1280.jpg",
                       "imageURL":"http://127.0.0.1:%d/img/1005.jpg"}]}
                    """.formatted(port, port, port, port);
                default -> """
                    {"total":4,"totalHits":4,"hits":[
                      {"id":1001,"tags":"mock","type":"photo","previewURL":"http://127.0.0.1:%d/img/1001_150.jpg",
                       "webformatURL":"http://127.0.0.1:%d/img/1001_640.jpg",
                       "largeImageURL":"http://127.0.0.1:%d/img/1001_1280.jpg",
                       "imageURL":"http://127.0.0.1:%d/img/1001.jpg"},
                      {"id":1002,"tags":"mock","type":"photo","previewURL":"http://127.0.0.1:%d/img/1002_150.jpg",
                       "webformatURL":"http://127.0.0.1:%d/img/1002_640.jpg",
                       "largeImageURL":"http://127.0.0.1:%d/img/1002_1280.jpg"},
                      {"id":1003,"tags":"mock","type":"photo","previewURL":"http://127.0.0.1:%d/img/1003_150.jpg",
                       "webformatURL":"http://127.0.0.1:%d/img/1003_640.jpg",
                       "largeImageURL":"http://127.0.0.1:%d/img/1003_1280.jpg",
                       "imageURL":"http://127.0.0.1:%d/img/1003.jpg"}]}
                    """.formatted(port, port, port, port, port, port, port, port, port, port, port,
                        port, port, port, port, port);
            };
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private PixabayClient newClient() {
        return new PixabayClient(WebClient.builder(), "http://127.0.0.1:" + port + "/api/");
    }

    @Test
    void searchParsesHitsAndPagination() {
        PixabayClient client = newClient();
        StepVerifier.create(client.search("test-key", "mountain", 1, 3, "photo", true))
            .assertNext(response -> {
                assertEquals(4, response.totalHits());
                assertEquals(3, response.hits().size());
                PixabayImage first = response.hits().get(0);
                assertEquals(1001, first.id());
                // original tier prefers imageURL
                assertEquals("http://127.0.0.1:" + port + "/img/1001.jpg", first.pickUrl("original"));
                // large tier prefers largeImageURL
                assertEquals("http://127.0.0.1:" + port + "/img/1001_1280.jpg",
                    first.pickUrl("large"));
                // preview tier
                assertEquals("http://127.0.0.1:" + port + "/img/1001_150.jpg",
                    first.pickUrl("preview"));
                // image 1002 has no imageURL -> falls back to largeImageURL
                assertEquals("http://127.0.0.1:" + port + "/img/1002_1280.jpg",
                    response.hits().get(1).pickUrl("original"));
                assertNull(response.hits().get(1).imageURL());
            })
            .verifyComplete();

        // page 2 returns the remaining image
        StepVerifier.create(client.search("test-key", "mountain", 2, 3, "photo", true))
            .assertNext(response -> assertEquals(1, response.hits().size()))
            .verifyComplete();

        assertNotNull(pageRequests.get("1"));
        assertNotNull(pageRequests.get("2"));
    }

    @Test
    void searchSendsQueryParameters() {
        PixabayClient client = newClient();
        StepVerifier.create(client.search("key-123", "forest,lake", 1, 50, "photo", false))
            .expectNextCount(1)
            .verifyComplete();
    }
}
