package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.backendcore.support.TestJwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for HTTP-level integration tests: the whole backend running against a
 * real PostgreSQL (one container and one Spring context shared by every test
 * class), called over HTTP with real JWT validation, like the clients do.
 *
 * <p>Every test creates its own users (random {@code sub}), so tests never
 * depend on each other's data.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        // Small AI quota so AiQuotaIntegrationTest can reach both limits quickly.
        "calorietracker.ai.rate-limit.per-minute=5",
        "calorietracker.ai.rate-limit.per-day=8"
})
public abstract class IntegrationTest {

    /** ECR Public mirror of the Docker Hub image: no pull rate limits in CI. */
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("public.ecr.aws/docker/library/postgres:16-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start(); // stopped by the JVM exit (Ryuk or not)
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static final HttpClient HTTP = HttpClient.newHttpClient();
    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String TODAY = LocalDate.now().toString();
    protected static final String YESTERDAY = LocalDate.now().minusDays(1).toString();

    /** Public seed food "Egg": 155 kcal, 13 P, 1.1 C, 11 F, 0 fiber per 100 g (V2/V4 migrations). */
    protected static final long PUBLIC_EGG_ID = 1;

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    // ---------------- users ----------------

    protected static String newSub() {
        return "user-" + UUID.randomUUID();
    }

    /** A fresh signed-in user. */
    protected static String newUser() {
        return TestJwts.forUser(newSub());
    }

    /** A fresh user with a complete profile (male, 30, 180 cm, 80 kg, moderate: BMR 1780, TDEE 2759). */
    protected String userWithProfile() throws Exception {
        String t = newUser();
        assertThat(put("/api/profile", t,
                "{\"sex\":\"MALE\",\"age\":30,\"heightCm\":180,\"weightKg\":80,\"activityLevel\":\"MODERATE\"}")
                .statusCode()).isEqualTo(200);
        return t;
    }

    // ---------------- HTTP ----------------

    protected HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    protected HttpResponse<String> post(String path, String token, String body) throws Exception {
        return send("POST", path, token, body);
    }

    protected HttpResponse<String> put(String path, String token, String body) throws Exception {
        return send("PUT", path, token, body);
    }

    protected HttpResponse<String> delete(String path, String token) throws Exception {
        return send("DELETE", path, token, null);
    }

    protected HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------------- JSON ----------------

    /** Parses the body, asserting the expected status first (with the body as the failure message). */
    /** A day's diary entries in meal order, read from the summary - the clients' only view of a day. */
    protected List<JsonNode> diaryEntries(String date, String token) throws Exception {
        JsonNode byMeal = json(get("/api/summary?date=" + date, token), 200).get("byMeal");
        List<JsonNode> entries = new ArrayList<>();
        for (String meal : List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK")) {
            byMeal.get(meal).get("entries").forEach(entries::add);
        }
        return entries;
    }

    protected static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    protected static long id(HttpResponse<String> created) {
        return json(created, 201).get("id").asLong();
    }
}
