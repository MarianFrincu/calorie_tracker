package com.calorietracker.desktop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.calorietracker.desktop.support.StubServer;
import org.junit.jupiter.api.Test;

class CognitoAuthServiceTest {

    private static final String POOL = "eu-central-1_Test1";
    private static final String AUTH_RESULT =
            "{\"AuthenticationResult\":{\"AccessToken\":\"access-1\",\"RefreshToken\":\"refresh-1\",\"ExpiresIn\":3600}}";

    /** Cognito's PASSWORD_VERIFIER challenge (values from the SRP test vectors). */
    private static final String CHALLENGE = "{\"ChallengeName\":\"PASSWORD_VERIFIER\",\"Session\":\"session-1\","
            + "\"ChallengeParameters\":{\"USER_ID_FOR_SRP\":\"user-uuid\",\"SALT\":\"a3f1c0de5b7e9a2c4d6f8e1b3a5c7d90\","
            + "\"SRP_B\":\"" + "c0ffee".repeat(64) + "\",\"SECRET_BLOCK\":\"c2VjcmV0IGJsb2Nr\"}}";

    @Test
    void loginProvesThePasswordWithoutSendingIt() throws Exception {
        try (StubServer cognito = new StubServer().then(200, CHALLENGE).then(200, AUTH_RESULT)) {
            CognitoAuthService auth = new CognitoAuthService(cognito.uri(), POOL, "client-1");
            CognitoAuthService.Tokens t = auth.login("a@b.c", "Secret-Passw0rd");
            assertEquals("access-1", t.accessToken());
            assertEquals("refresh-1", t.refreshToken());

            StubServer.Request start = cognito.requests.get(0);
            assertEquals("AWSCognitoIdentityProviderService.InitiateAuth", start.header("X-Amz-Target"));
            assertEquals("application/x-amz-json-1.1", start.header("Content-Type"));
            assertTrue(start.body().contains("\"USER_SRP_AUTH\""));
            assertTrue(start.body().contains("\"SRP_A\""));
            assertTrue(start.body().contains("\"client-1\""));

            StubServer.Request respond = cognito.requests.get(1);
            assertEquals("AWSCognitoIdentityProviderService.RespondToAuthChallenge", respond.header("X-Amz-Target"));
            assertTrue(respond.body().contains("\"PASSWORD_VERIFIER\""));
            assertTrue(respond.body().contains("\"USERNAME\":\"user-uuid\""));
            assertTrue(respond.body().contains("\"PASSWORD_CLAIM_SECRET_BLOCK\":\"c2VjcmV0IGJsb2Nr\""));
            assertTrue(respond.body().contains("\"Session\":\"session-1\""));
            assertTrue(respond.body().contains("\"PASSWORD_CLAIM_SIGNATURE\""));
            assertFalse(start.body().contains("Secret-Passw0rd") || respond.body().contains("Secret-Passw0rd"));
        }
    }

    @Test
    void refreshKeepsTheExistingRefreshToken() throws Exception {
        try (StubServer cognito = new StubServer().then(200, "{\"AuthenticationResult\":{\"AccessToken\":\"access-2\"}}")) {
            CognitoAuthService.Tokens t = new CognitoAuthService(cognito.uri(), POOL, "c").refresh("refresh-1");
            assertEquals("access-2", t.accessToken());
            assertEquals("refresh-1", t.refreshToken());
            assertTrue(cognito.requests.get(0).body().contains("REFRESH_TOKEN_AUTH"));
        }
    }

    @Test
    void challengesAndErrorsAreReadableWithoutCodes() throws Exception {
        try (StubServer cognito = new StubServer()
                .then(200, "{\"ChallengeName\":\"NEW_PASSWORD_REQUIRED\",\"Session\":\"x\"}")
                .then(400, "{\"__type\":\"NotAuthorizedException\",\"message\":\"Incorrect username or password.\"}")
                .then(400, "{\"__type\":\"com.amazonaws#UserNotConfirmedException\",\"message\":\"User is not confirmed.\"}")) {
            CognitoAuthService auth = new CognitoAuthService(cognito.uri(), POOL, "c");
            CognitoException challenge = assertThrows(CognitoException.class, () -> auth.login("a", "b"));
            assertEquals("NEW_PASSWORD_REQUIRED", challenge.type());
            CognitoException wrong = assertThrows(CognitoException.class, () -> auth.login("a", "b"));
            assertEquals("Incorrect email or password.", wrong.getMessage());
            CognitoException unconfirmed = assertThrows(CognitoException.class, () -> auth.login("a", "b"));
            assertEquals("UserNotConfirmedException", unconfirmed.type());
            assertFalse(unconfirmed.getMessage().matches(".*(\\d{3}|Exception|InitiateAuth).*"));
        }
    }

    @Test
    void accountActionsUseTheirCognitoOperations() throws Exception {
        try (StubServer cognito = new StubServer()
                .then(200, "{}").then(200, "{}").then(200, "{}").then(200, "{}").then(200, "{}").then(200, "{}").then(200, "{}")) {
            CognitoAuthService auth = new CognitoAuthService(cognito.uri(), POOL, "c");
            auth.signUp("a@b.c", "pw");
            auth.confirmSignUp("a@b.c", "123456");
            auth.resendCode("a@b.c");
            auth.forgotPassword("a@b.c");
            auth.confirmForgotPassword("a@b.c", "123456", "new-pw");
            auth.revoke("refresh-1");
            auth.deleteUser("access-1");
            assertEquals(java.util.List.of("SignUp", "ConfirmSignUp", "ResendConfirmationCode", "ForgotPassword",
                            "ConfirmForgotPassword", "RevokeToken", "DeleteUser"),
                    cognito.requests.stream().map(r -> r.header("X-Amz-Target").replace("AWSCognitoIdentityProviderService.", "")).toList());
        }
    }

    @Test
    void rejectsBadPoolIdsAndMissingClientIds() {
        assertThrows(IllegalArgumentException.class, () -> new CognitoAuthService("evil.com/x_1", "c"));
        assertThrows(IllegalArgumentException.class, () -> new CognitoAuthService("", "c"));
        assertThrows(IllegalArgumentException.class, () -> new CognitoAuthService("eu-central-1", "c"));
        assertThrows(IllegalArgumentException.class, () -> new CognitoAuthService("eu-central-1_Abc", " "));
    }
}
