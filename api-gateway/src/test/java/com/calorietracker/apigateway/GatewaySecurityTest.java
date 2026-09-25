package com.calorietracker.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.apigateway.support.TestJwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Edge behaviour of the gateway: authentication and security headers.
 * No downstream services run here, so an authenticated /api call ends in 503
 * (no instance to route to) - which is exactly how we know it got past auth.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewaySecurityTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIsPublicAndTerse() throws Exception {
        HttpResponse<String> r = send(get("/actuator/health"));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("UP").doesNotContain("components");
    }

    @Test
    void apiRequiresAValidTokenAndErrorsCarrySecurityHeaders() throws Exception {
        HttpResponse<String> r = send(get("/api/profile"));
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(r.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(r.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(r.headers().firstValue("Content-Security-Policy")).isPresent();

        assertThat(send(get("/api/profile").header("Authorization", "Bearer " + TestJwts.expired("u")))
                .statusCode()).isEqualTo(401);
        assertThat(send(get("/api/profile").header("Authorization", "Bearer " + TestJwts.idToken("u")))
                .statusCode()).isEqualTo(401);
        assertThat(send(get("/api/profile").header("Authorization", "Bearer " + TestJwts.forOtherClient("u")))
                .statusCode()).isEqualTo(401);
        assertThat(send(get("/api/profile").header("Authorization", "Bearer " + TestJwts.forUser("u")))
                .statusCode()).isEqualTo(503);
    }

    @Test
    void crossOriginBrowserCallsAreNotAllowed() throws Exception {
        // The web app is served from the same origin as /api, so the gateway
        // grants no cross-origin access at all: a preflight gets no CORS headers.
        HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/profile"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"));
        assertThat(r.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void refusesViewersBelowTls12() throws Exception {
        HttpResponse<String> old = send(get("/api/profile").header("CloudFront-Viewer-TLS", "TLSv1.1:ECDHE-RSA-AES128-SHA:fullHandshake"));
        assertThat(old.statusCode()).isEqualTo(426);
        assertThat(old.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(send(get("/api/profile").header("CloudFront-Viewer-TLS", "TLSv1:ECDHE-RSA-AES128-SHA:fullHandshake"))
                .statusCode()).isEqualTo(426);
        // TLS 1.2+ (and requests that didn't come through CloudFront) carry on to authentication.
        assertThat(send(get("/api/profile").header("CloudFront-Viewer-TLS", "TLSv1.3:TLS_AES_128_GCM_SHA256:fullHandshake"))
                .statusCode()).isEqualTo(401);
        assertThat(send(get("/api/profile").header("CloudFront-Viewer-TLS", "TLSv1.2:ECDHE-RSA-AES128-GCM-SHA256:sessionResumed"))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void hstsOnlyWhenTheClientLegWasHttps() throws Exception {
        assertThat(send(get("/actuator/health")).headers().firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(send(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .headers().firstValue("Strict-Transport-Security")).isPresent();
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    }

    private static HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
