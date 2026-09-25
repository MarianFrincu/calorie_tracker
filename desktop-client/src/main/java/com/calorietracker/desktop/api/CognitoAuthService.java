package com.calorietracker.desktop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Cognito IDP client. Calls the AWS Cognito HTTPS endpoint directly so
 * no AWS SDK is needed. Flows used by the desktop app:
 *   - login()                 InitiateAuth / USER_SRP_AUTH + RespondToAuthChallenge
 *                             (SRP: the password never leaves this machine, see CognitoSrp)
 *   - refresh()               InitiateAuth / REFRESH_TOKEN_AUTH
 *   - revoke()                RevokeToken (sign-out: kills the refresh token server-side)
 *   - signUp() / confirmSignUp() / resendCode()
 *   - forgotPassword() / confirmForgotPassword()
 *   - deleteUser()            DeleteUser (account deletion, authorised by the access token)
 *
 * All traffic goes through {@link SecureHttp} (TLS 1.2+, verified certificates).
 */
public class CognitoAuthService {

    /** Access token for the API plus, on login, the long-lived refresh token. */
    public record Tokens(String accessToken, String refreshToken) {}

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final String userPoolId;
    private final String clientId;

    /** @param userPoolId e.g. eu-central-1_AbCdEf123 - the region is its prefix */
    public CognitoAuthService(String userPoolId, String clientId) {
        this(regionalEndpoint(userPoolId), userPoolId, clientId);
    }

    /** Test hook: talk to a stand-in endpoint instead of cognito-idp.&lt;region&gt;.amazonaws.com. */
    CognitoAuthService(URI endpoint, String userPoolId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("cognito.client.id is not configured");
        }
        this.endpoint = endpoint;
        this.userPoolId = userPoolId;
        this.clientId = clientId;
        this.http = SecureHttp.newClient();
    }

    private static URI regionalEndpoint(String userPoolId) {
        // Comes from a local config file we don't fully trust at the binary boundary: refuse
        // anything not shaped like a real pool id, so it can never point the request elsewhere.
        if (userPoolId == null || !userPoolId.matches("[a-z]{2}(-[a-z]+)+-\\d+_[0-9A-Za-z]+")) {
            throw new IllegalArgumentException("Invalid or missing Cognito user pool id: " + userPoolId);
        }
        return URI.create("https://cognito-idp." + userPoolId.substring(0, userPoolId.indexOf('_')) + ".amazonaws.com/");
    }

    /** SRP sign-in: two round trips, and the password itself is never sent. */
    public Tokens login(String username, String password) throws Exception {
        CognitoSrp srp = new CognitoSrp();
        JsonNode started = mapper.readTree(call("InitiateAuth", Map.of(
                "AuthFlow", "USER_SRP_AUTH",
                "ClientId", clientId,
                "AuthParameters", Map.of("USERNAME", username, "SRP_A", srp.srpA()))));
        if (!"PASSWORD_VERIFIER".equals(started.path("ChallengeName").asText(null))) {
            return tokens(started);
        }
        JsonNode p = started.path("ChallengeParameters");
        CognitoSrp.Challenge challenge = new CognitoSrp.Challenge(p.path("USER_ID_FOR_SRP").asText(),
                p.path("SALT").asText(), p.path("SRP_B").asText(), p.path("SECRET_BLOCK").asText());
        CognitoSrp.Claim claim = srp.claim(userPoolId, password, challenge, Instant.now());

        Map<String, Object> respond = new LinkedHashMap<>();
        respond.put("ChallengeName", "PASSWORD_VERIFIER");
        respond.put("ClientId", clientId);
        if (started.hasNonNull("Session")) respond.put("Session", started.get("Session").asText());
        respond.put("ChallengeResponses", Map.of(
                "USERNAME", challenge.userIdForSrp(),
                "PASSWORD_CLAIM_SECRET_BLOCK", challenge.secretBlock(),
                "TIMESTAMP", claim.timestamp(),
                "PASSWORD_CLAIM_SIGNATURE", claim.signature()));
        return tokens(mapper.readTree(call("RespondToAuthChallenge", respond)));
    }

    /** New access token from a refresh token. The refresh token itself is unchanged. */
    public Tokens refresh(String refreshToken) throws Exception {
        Tokens t = tokens(mapper.readTree(call("InitiateAuth", Map.of(
                "AuthFlow", "REFRESH_TOKEN_AUTH",
                "ClientId", clientId,
                "AuthParameters", Map.of("REFRESH_TOKEN", refreshToken)))));
        return new Tokens(t.accessToken(), refreshToken);
    }

    public void revoke(String refreshToken) throws Exception {
        call("RevokeToken", Map.of("ClientId", clientId, "Token", refreshToken));
    }

    public void signUp(String email, String password) throws Exception {
        call("SignUp", Map.of(
                "ClientId", clientId,
                "Username", email,
                "Password", password,
                "UserAttributes", List.of(Map.of("Name", "email", "Value", email))));
    }

    public void confirmSignUp(String email, String code) throws Exception {
        call("ConfirmSignUp", Map.of("ClientId", clientId, "Username", email, "ConfirmationCode", code));
    }

    public void resendCode(String email) throws Exception {
        call("ResendConfirmationCode", Map.of("ClientId", clientId, "Username", email));
    }

    public void forgotPassword(String email) throws Exception {
        call("ForgotPassword", Map.of("ClientId", clientId, "Username", email));
    }

    public void confirmForgotPassword(String email, String code, String newPassword) throws Exception {
        call("ConfirmForgotPassword", Map.of(
                "ClientId", clientId, "Username", email,
                "ConfirmationCode", code, "Password", newPassword));
    }

    public void deleteUser(String accessToken) throws Exception {
        call("DeleteUser", Map.of("AccessToken", accessToken));
    }

    private Tokens tokens(JsonNode root) {
        String challenge = root.path("ChallengeName").asText(null);
        if (challenge != null) {
            // e.g. NEW_PASSWORD_REQUIRED for an admin-created user, or MFA.
            throw new CognitoException(challenge, "This account needs an extra sign-in step the app doesn't "
                    + "support. Use \"Forgot password?\" to set a new password.");
        }
        JsonNode result = root.path("AuthenticationResult");
        String access = result.path("AccessToken").asText(null);
        if (access == null || access.isBlank()) {
            throw new CognitoException("NoToken", "Sign-in failed. Please try again.");
        }
        return new Tokens(access, result.path("RefreshToken").asText(null));
    }

    private String call(String action, Map<String, Object> payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Target", "AWSCognitoIdentityProviderService." + action)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            // Cognito returns {"__type":"[namespace#]SomeException","message":"..."}.
            String type = "";
            String message = "";
            try {
                JsonNode n = mapper.readTree(resp.body());
                String raw = n.path("__type").asText("");
                type = raw.substring(raw.lastIndexOf('#') + 1);
                message = n.path("message").asText("");
            } catch (Exception ignored) {
                // not JSON: the generic sentence below
            }
            throw new CognitoException(type, CognitoException.friendly(type, message));
        }
        return resp.body();
    }
}
