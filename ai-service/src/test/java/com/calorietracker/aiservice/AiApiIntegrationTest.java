package com.calorietracker.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.support.TestJwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import com.calorietracker.aiservice.ratelimit.AiQuota;
import com.calorietracker.aiservice.ratelimit.RateLimitExceededException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "calorietracker.ai.provider=mock"
})
class AiApiIntegrationTest {

    /** Stand-in for backend-core's quota (tested there): 3 calls per user. */
    @TestConfiguration
    static class ThreeCallsPerUser {
        @Bean
        @Primary
        AiQuota threeCallsPerUser() {
            Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
            return () -> {
                String user = SecurityContextHolder.getContext().getAuthentication().getName();
                if (calls.computeIfAbsent(user, u -> new AtomicInteger()).incrementAndGet() > 3) {
                    throw new RateLimitExceededException(Duration.ofSeconds(42));
                }
            };
        }
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    void parsesWithAValidToken() throws Exception {
        HttpResponse<String> r = post("/api/ai/parse", token(), "{\"text\":\"chicken 200g\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"calories\":330");
    }

    @Test
    void rejectsMissingTokenAndBadInput() throws Exception {
        assertThat(post("/api/ai/parse", null, "{\"text\":\"egg\"}").statusCode()).isEqualTo(401);
        assertThat(post("/api/ai/parse", TestJwts.idToken("u"), "{\"text\":\"egg\"}").statusCode()).isEqualTo(401);
        assertThat(post("/api/ai/parse", TestJwts.forOtherClient("u"), "{\"text\":\"egg\"}").statusCode()).isEqualTo(401);
        String t = token();
        assertThat(post("/api/ai/parse", t, "{\"text\":").statusCode()).isEqualTo(400);
        assertThat(post("/api/ai/parse", t, "{\"text\":\"   \"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/ai/parse", t, "{\"text\":\"" + "a".repeat(2001) + "\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/ai/nope", t, "{}").statusCode()).isEqualTo(404);
    }

    @Test
    void rateLimitsEachUser() throws Exception {
        String greedy = token();
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/ai/parse", greedy, "{\"text\":\"egg\"}").statusCode()).isEqualTo(200);
        }
        HttpResponse<String> limited = post("/api/ai/parse-recipe", greedy, "{\"text\":\"egg\"}");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).contains("42");
        assertThat(limited.body()).contains("AI limit reached");

        // Someone else is unaffected.
        assertThat(post("/api/ai/parse", token(), "{\"text\":\"egg\"}").statusCode()).isEqualTo(200);
    }

    private static String token() {
        return TestJwts.forUser("user-" + UUID.randomUUID());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
