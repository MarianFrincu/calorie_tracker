package com.calorietracker.desktop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.calorietracker.desktop.support.StubServer;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiClientTest {

    @TempDir
    Path dir;

    private static final String PROFILE = "{\"id\":1,\"userKey\":\"u\",\"displayName\":\"Me\",\"sex\":\"MALE\",\"age\":30,"
            + "\"heightCm\":180,\"weightKg\":80,\"activityLevel\":\"MODERATE\",\"bmr\":1780,\"tdeeMaintain\":2759}";

    @BeforeEach
    void isolateSessionFile() {
        SessionStore.useFile(dir.resolve("session.properties"));
    }

    private static String jwt(String id) {
        String payload = "{\"sub\":\"u\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + ",\"jti\":\"" + id + "\"}";
        return "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + ".s";
    }

    @Test
    void withoutAuthItSendsTimeZoneAndNoToken() throws Exception {
        try (StubServer api = new StubServer().then(200, PROFILE)) {
            ApiClient client = new ApiClient(api.baseUrl(), null);
            assertEquals(1780, client.getProfile().bmr());
            StubServer.Request r = api.requests.get(0);
            assertEquals("/api/profile", r.uri());
            assertEquals(ZoneId.systemDefault().getId(), r.header("X-Time-Zone"));
            assertNull(r.header("Authorization"));
        }
    }

    @Test
    void anExpiredTokenIsRefreshedOnceAndTheCallRetried() throws Exception {
        String first = jwt("1");
        String second = jwt("2");
        try (StubServer api = new StubServer().then(401, "").then(200, PROFILE);
             StubServer cognito = new StubServer().then(200, "{\"AuthenticationResult\":{\"AccessToken\":\"" + second + "\"}}")) {
            AuthSession session = new AuthSession(new CognitoAuthService(cognito.uri(), "eu-central-1_Test1", "c"));
            session.signedIn(new CognitoAuthService.Tokens(first, "refresh-1"));
            ApiClient client = new ApiClient(api.baseUrl(), session);

            assertEquals(2759, client.getProfile().tdeeMaintain());
            assertEquals("Bearer " + first, api.requests.get(0).header("Authorization"));
            assertEquals("Bearer " + second, api.requests.get(1).header("Authorization"));
            assertEquals(second, session.accessToken());
            assertEquals(second, SessionStore.load().accessToken()); // persisted
        }
    }

    @Test
    void whenRefreshFailsTheSessionEndsAndTheUiIsTold() throws Exception {
        try (StubServer api = new StubServer().then(401, "");
             StubServer cognito = new StubServer().then(400, "{\"message\":\"Refresh Token has been revoked\"}")) {
            AuthSession session = new AuthSession(new CognitoAuthService(cognito.uri(), "eu-central-1_Test1", "c"));
            session.signedIn(new CognitoAuthService.Tokens(jwt("1"), "revoked"));
            ApiClient client = new ApiClient(api.baseUrl(), session);
            AtomicBoolean expired = new AtomicBoolean();
            client.setOnSessionExpired(() -> expired.set(true));

            ApiException e = assertThrows(ApiException.class, client::getProfile);
            assertEquals(401, e.status);
            assertTrue(expired.get());
            assertFalse(session.isSignedIn());
            assertNull(SessionStore.load());
        }
    }

    @Test
    void errorsCarryStatusAndTheServersMessage() throws Exception {
        try (StubServer api = new StubServer().then(409,
                "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Cannot delete \\\"Oats\\\" - it is used in 1 recipe line item(s).\"}")) {
            ApiClient client = new ApiClient(api.baseUrl(), null);
            ApiException e = assertThrows(ApiException.class, () -> client.deleteIngredient(5));
            assertEquals(409, e.status);
            assertEquals("Cannot delete \"Oats\" - it is used in 1 recipe line item(s).",
                    com.calorietracker.desktop.ui.Messages.friendly(e));
            assertEquals("DELETE", api.requests.get(0).method());
            assertEquals("/api/ingredients/5", api.requests.get(0).uri());
        }
    }

    @Test
    void requestsHaveTheExpectedShape() throws Exception {
        try (StubServer api = new StubServer().then(201, "{\"ml\":250,\"id\":1}").then(204, "").then(200, "[]")) {
            ApiClient client = new ApiClient(api.baseUrl(), null);
            client.addWater(LocalDate.of(2026, 9, 24), 250);
            client.deleteProfile();
            client.searchAllIngredients("egg & rice", 2, 20);
            assertEquals("POST", api.requests.get(0).method());
            assertEquals("{\"date\":\"2026-09-24\",\"ml\":250}", api.requests.get(0).body());
            assertEquals("DELETE", api.requests.get(1).method());
            assertEquals("/api/profile", api.requests.get(1).uri());
            assertEquals("/api/ingredients?q=egg+%26+rice&page=2&size=20&scope=all", api.requests.get(2).uri());
        }
    }

    @Test
    void refusesToSendTokensOverPlainHttpToAnotherMachine() {
        AuthSession session = new AuthSession(new CognitoAuthService(java.net.URI.create("https://x/"), "eu-central-1_Test1", "c"));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiClient("http://calorietracker.example.com", session));
    }
}
