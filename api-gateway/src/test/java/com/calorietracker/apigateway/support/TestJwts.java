package com.calorietracker.apigateway.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Mints RS256 access tokens signed with the test-only key in
 * {@code src/test/resources/test-keys}. The app under test is pointed at the
 * matching public key, so these tokens authenticate exactly like Cognito's.
 */
public final class TestJwts {

    private static final NimbusJwtEncoder ENCODER = encoder();

    /** The app client id the test profile trusts (application-test.yml). */
    public static final String CLIENT_ID = "test-client";

    private TestJwts() {}

    public static String forUser(String sub) {
        return mint(sub, Instant.now().plusSeconds(3600));
    }

    public static String expired(String sub) {
        return mint(sub, Instant.now().minusSeconds(60));
    }

    /** A validly signed Cognito-style ID token: must NOT be accepted as an API credential. */
    public static String idToken(String sub) {
        return mint(sub, Instant.now().plusSeconds(3600), "id");
    }

    /** A valid access token of ANOTHER app client of the same pool: must NOT be accepted. */
    public static String forOtherClient(String sub) {
        return mint(sub, Instant.now().plusSeconds(3600), "access", "some-other-client");
    }

    private static String mint(String sub, Instant expiresAt) {
        return mint(sub, expiresAt, "access");
    }

    private static String mint(String sub, Instant expiresAt, String tokenUse) {
        return mint(sub, expiresAt, tokenUse, CLIENT_ID);
    }

    private static String mint(String sub, Instant expiresAt, String tokenUse, String clientId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(sub)
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("client_id", clientId)
                .claim("token_use", tokenUse)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static NimbusJwtEncoder encoder() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pem("test-private.pem")));
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pem("test-public.pem")));
            RSAKey jwk = new RSAKey.Builder(pub).privateKey(priv).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load test signing key", e);
        }
    }

    private static byte[] pem(String name) throws Exception {
        try (InputStream in = TestJwts.class.getResourceAsStream("/test-keys/" + name)) {
            String body = new String(in.readAllBytes()).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return Base64.getDecoder().decode(body);
        }
    }
}
