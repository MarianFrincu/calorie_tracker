package com.calorietracker.desktop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * srp-vectors.json was produced by AWS's amazon-cognito-identity-js 6.3.20
 * (AuthenticationHelper and CognitoUser's signature code) from fixed inputs,
 * so matching it means Cognito computes the same values. The web client's
 * srp.test.ts checks the same vectors.
 */
class CognitoSrpTest {

    @Test
    void matchesTheAwsLibrary() throws Exception {
        JsonNode vectors;
        try (InputStream in = getClass().getResourceAsStream("/srp-vectors.json")) {
            vectors = new ObjectMapper().readTree(in);
        }
        assertEquals(2, vectors.size());
        for (JsonNode v : vectors) {
            CognitoSrp srp = new CognitoSrp(new BigInteger(v.get("smallAHex").asText(), 16));
            assertEquals(v.get("largeAHex").asText(), srp.srpA());

            String poolId = v.get("poolId").asText();
            CognitoSrp.Challenge challenge = new CognitoSrp.Challenge(v.get("userId").asText(),
                    v.get("saltHex").asText(), v.get("serverBHex").asText(), v.get("secretBlock").asText());
            byte[] key = srp.passwordAuthenticationKey(poolId.substring(poolId.indexOf('_') + 1), challenge,
                    v.get("password").asText());
            assertEquals(v.get("hkdfHex").asText(), HexFormat.of().formatHex(key), poolId);

            CognitoSrp.Claim claim = srp.claim(poolId, v.get("password").asText(), challenge,
                    Instant.parse("2026-09-05T09:03:07Z"));
            assertEquals(v.get("timestamp").asText(), claim.timestamp());
            assertEquals(v.get("signature").asText(), claim.signature(), poolId);
        }
    }

    @Test
    void padsLikeTheLibrary() {
        assertEquals("14", CognitoSrp.padHex(BigInteger.valueOf(20)));
        assertEquals("00ec", CognitoSrp.padHex(BigInteger.valueOf(236)));
        assertEquals("0f1e", CognitoSrp.padHex(new BigInteger("f1e", 16)));
    }

    @Test
    void everyAttemptUsesAFreshSecret() {
        assertNotEquals(new CognitoSrp().srpA(), new CognitoSrp().srpA());
    }
}
