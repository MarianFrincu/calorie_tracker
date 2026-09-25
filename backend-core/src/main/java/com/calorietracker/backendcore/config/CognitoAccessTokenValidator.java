package com.calorietracker.backendcore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Accepts only Cognito <em>access</em> tokens issued to this app's client.
 *
 * <p>The decoder's signature, expiry and issuer checks prove a token came from
 * our user pool - but the pool also signs tokens that must not work here:
 * <ul>
 *   <li>ID tokens (same key, same issuer). They are for the client app, not
 *       for calling APIs; Cognito marks the difference in {@code token_use}.</li>
 *   <li>Access tokens of any other app client of the pool. {@code client_id}
 *       pins the token to the web/desktop app client.</li>
 * </ul>
 * Spring Boot adds every {@code OAuth2TokenValidator<Jwt>} bean to the
 * auto-configured JWT decoder.
 */
@Component
@Profile("!dev")
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_AN_ACCESS_TOKEN =
            new OAuth2Error("invalid_token", "Only access tokens are accepted", null);
    private static final OAuth2Error WRONG_CLIENT =
            new OAuth2Error("invalid_token", "Token was issued to another client", null);

    private final String clientId;

    public CognitoAccessTokenValidator(@Value("${calorietracker.security.client-id}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            // Fail at startup rather than silently accepting every client.
            throw new IllegalStateException("calorietracker.security.client-id must be set");
        }
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return OAuth2TokenValidatorResult.failure(NOT_AN_ACCESS_TOKEN);
        }
        return clientId.equals(jwt.getClaimAsString("client_id"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(WRONG_CLIENT);
    }
}
