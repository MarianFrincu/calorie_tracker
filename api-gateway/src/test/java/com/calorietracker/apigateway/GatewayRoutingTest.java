package com.calorietracker.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.apigateway.support.TestJwts;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Routing through the real gateway to two stand-in services, registered via
 * Spring Cloud's simple discovery client instead of Eureka.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false"
})
class GatewayRoutingTest {

    /** What a stub service saw. */
    record Seen(String service, String method, String uri, Map<String, List<String>> headers) {}

    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    private static final HttpServer BACKEND = stub("backend-core");
    private static final HttpServer AI = stub("ai-service");
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static HttpServer stub(String name) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", ex -> {
                SEEN.add(new Seen(name, ex.getRequestMethod(), ex.getRequestURI().toString(), ex.getRequestHeaders()));
                byte[] body = ("{\"from\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.backend-core[0].uri",
                () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.ai-service[0].uri",
                () -> "http://127.0.0.1:" + AI.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
        AI.stop(0);
    }

    @BeforeEach
    void reset() {
        SEEN.clear();
    }

    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> call(String method, String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Time-Zone", "Europe/Bucharest")
                .header("Content-Type", "application/json")
                .method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString("{\"text\":\"egg\"}"));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void apiCallsGoToBackendCoreWithTheirHeadersAndQuery() throws Exception {
        String token = TestJwts.forUser("routing-user");
        HttpResponse<String> r = call("GET", "/api/summary?date=2026-09-24", token);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("backend-core");
        Seen seen = SEEN.get(0);
        assertThat(seen.uri()).isEqualTo("/api/summary?date=2026-09-24");
        assertThat(seen.headers().get("Authorization")).containsExactly("Bearer " + token);
        assertThat(seen.headers().get("X-time-zone")).containsExactly("Europe/Bucharest");
    }

    @Test
    void aiCallsGoToTheAiService() throws Exception {
        HttpResponse<String> r = call("POST", "/api/ai/parse", TestJwts.forUser("routing-user"));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("ai-service");
        assertThat(SEEN).extracting(Seen::service, Seen::method).containsExactly(org.assertj.core.groups.Tuple.tuple("ai-service", "POST"));
    }

    @Test
    void recipesFromAiStayOnBackendCore() throws Exception {
        assertThat(call("POST", "/api/recipes/from-ai", TestJwts.forUser("u")).body()).contains("backend-core");
    }

    @Test
    void proxiedResponsesCarrySecurityHeaders() throws Exception {
        HttpResponse<String> r = call("GET", "/api/profile", TestJwts.forUser("u"));
        assertThat(r.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(r.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
    }

    @Test
    void rejectedRequestsNeverReachAService() throws Exception {
        assertThat(call("GET", "/api/profile", null).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/ai/parse", TestJwts.idToken("u")).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/profile", TestJwts.expired("u")).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/profile", TestJwts.forOtherClient("u")).statusCode()).isEqualTo(401);
        assertThat(SEEN).isEmpty();
    }

    @Test
    void nonApiPathsAreNotRouted() throws Exception {
        assertThat(call("GET", "/eureka/apps", TestJwts.forUser("u")).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/actuator/gateway/routes", null).statusCode()).isIn(401, 404);
        assertThat(SEEN).isEmpty();
    }
}
