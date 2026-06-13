package com.calorietracker.desktop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal Cognito IDP client. Calls the AWS Cognito HTTPS endpoint directly so
 * no AWS SDK is needed. Supports the three flows the desktop app uses:
 *   - login()         (InitiateAuth / USER_PASSWORD_AUTH)
 *   - signUp()        (SignUp)
 *   - confirmSignUp() (ConfirmSignUp with the emailed verification code)
 */
public class CognitoAuthService {

    /** Bounded so a hung network can't freeze the login dialog forever. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String login(String region, String clientId, String username, String password) throws Exception {
        Map<String, Object> payload = Map.of(
                "AuthFlow", "USER_PASSWORD_AUTH",
                "ClientId", clientId,
                "AuthParameters", Map.of("USERNAME", username, "PASSWORD", password));
        String body = call(region, "InitiateAuth", payload);
        JsonNode root = mapper.readTree(body);
        return root.path("AuthenticationResult").path("AccessToken").asText(null);
    }

    public void signUp(String region, String clientId, String email, String password) throws Exception {
        Map<String, Object> payload = Map.of(
                "ClientId", clientId,
                "Username", email,
                "Password", password,
                "UserAttributes", List.of(Map.of("Name", "email", "Value", email)));
        call(region, "SignUp", payload);
    }

    public void confirmSignUp(String region, String clientId, String email, String code) throws Exception {
        Map<String, Object> payload = Map.of(
                "ClientId", clientId,
                "Username", email,
                "ConfirmationCode", code);
        call(region, "ConfirmSignUp", payload);
    }

    private String call(String region, String action, Map<String, Object> payload) throws Exception {
        if (!region.matches("[a-z0-9-]+")) {
            // Region comes from a local config file we don't fully trust at the binary boundary;
            // refuse anything that doesn't look like a real AWS region (no slashes/dots/etc).
            throw new IllegalArgumentException("Invalid AWS region: " + region);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://cognito-idp." + region + ".amazonaws.com/"))
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Target", "AWSCognitoIdentityProviderService." + action)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException(action + " failed (" + resp.statusCode() + "): " + extractMessage(resp.body()));
        }
        return resp.body();
    }

    /** Cognito returns errors as {"__type":"...Exception","message":"..."}. Surface just the message. */
    private String extractMessage(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String msg = n.path("message").asText(null);
            return msg == null ? body : msg;
        } catch (Exception ignored) {
            return body;
        }
    }
}
