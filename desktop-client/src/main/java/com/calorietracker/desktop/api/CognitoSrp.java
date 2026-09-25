package com.calorietracker.desktop.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The client half of Cognito's USER_SRP_AUTH (Secure Remote Password, SRP-6a).
 *
 * <p>The password never leaves this machine: sign-in sends a random public
 * value A, Cognito answers with its own public value B plus a salt, and the
 * client proves it knows the password by signing Cognito's secret block with
 * a key only the right password can derive. Neither side can learn the
 * password from what crosses the wire.
 *
 * <p>Byte-for-byte the algorithm of AWS's amazon-cognito-identity-js
 * (AuthenticationHelper + CognitoUser) - the web client's srp.ts is the same
 * code in TypeScript. CognitoSrpTest pins it to values produced by that library.
 */
public final class CognitoSrp {

    /** RFC 5054's 3072-bit group, the one Cognito uses. */
    private static final BigInteger N = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
            + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
            + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
            + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
            + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
            + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
            + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64"
            + "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
            + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B"
            + "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
            + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31"
            + "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16);
    private static final BigInteger G = BigInteger.TWO;
    private static final BigInteger K = new BigInteger(1, sha256(hex(padHex(N) + padHex(G))));
    private static final SecureRandom RANDOM = new SecureRandom();
    /** "Sat Sep 5 09:03:07 UTC 2026": day not zero-padded, the rest is. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss 'UTC' yyyy", Locale.US).withZone(ZoneOffset.UTC);

    /** What Cognito's PASSWORD_VERIFIER challenge sends back. */
    public record Challenge(String userIdForSrp, String saltHex, String serverBHex, String secretBlock) {}

    /** The challenge responses to send back, besides USERNAME and the secret block. */
    public record Claim(String timestamp, String signature) {}

    private final BigInteger smallA;
    private final BigInteger largeA;

    /** A fresh attempt with a random secret. */
    public CognitoSrp() {
        this(new BigInteger(1, randomBytes(128)).mod(N));
    }

    /** Tests only: a fixed secret. */
    CognitoSrp(BigInteger smallA) {
        this.smallA = smallA;
        this.largeA = G.modPow(smallA, N);
        if (largeA.mod(N).signum() == 0) {
            throw new IllegalStateException("Invalid SRP value; please try again.");
        }
    }

    /** The SRP_A auth parameter. */
    public String srpA() {
        return largeA.toString(16);
    }

    /** The PASSWORD_CLAIM_SIGNATURE for the challenge, computed for {@code now}. */
    public Claim claim(String userPoolId, String password, Challenge challenge, Instant now) {
        String poolName = userPoolId.substring(userPoolId.indexOf('_') + 1);
        byte[] key = passwordAuthenticationKey(poolName, challenge, password);
        String timestamp = TIMESTAMP.format(now);
        byte[] message = concat(
                poolName.getBytes(StandardCharsets.UTF_8),
                challenge.userIdForSrp().getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(challenge.secretBlock()),
                timestamp.getBytes(StandardCharsets.UTF_8));
        return new Claim(timestamp, Base64.getEncoder().encodeToString(hmac(key, message)));
    }

    /** HKDF key both sides derive from the shared SRP secret. Package-private for tests. */
    byte[] passwordAuthenticationKey(String poolName, Challenge challenge, String password) {
        BigInteger b = new BigInteger(challenge.serverBHex(), 16);
        if (b.mod(N).signum() == 0) throw new IllegalStateException("Invalid SRP value from Cognito.");
        BigInteger u = new BigInteger(1, sha256(hex(padHex(largeA) + padHex(b))));
        if (u.signum() == 0) throw new IllegalStateException("Invalid SRP value from Cognito.");

        String userPasswordHash = HexFormat.of().formatHex(
                sha256((poolName + challenge.userIdForSrp() + ":" + password).getBytes(StandardCharsets.UTF_8)));
        BigInteger x = new BigInteger(1, sha256(hex(padHex(new BigInteger(challenge.saltHex(), 16)) + userPasswordHash)));

        BigInteger base = b.subtract(K.multiply(G.modPow(x, N))).mod(N);
        BigInteger s = base.modPow(smallA.add(u.multiply(x)), N);

        // HKDF-SHA256, info "Caldera Derived Key", first 16 bytes.
        byte[] prk = hmac(hex(padHex(u)), hex(padHex(s)));
        byte[] okm = hmac(prk, concat("Caldera Derived Key".getBytes(StandardCharsets.UTF_8), new byte[] {1}));
        return Arrays.copyOf(okm, 16);
    }

    /** Even-length hex, with a leading 00 when the top bit is set (a positive two's-complement encoding). */
    static String padHex(BigInteger n) {
        String hex = n.toString(16);
        if (hex.length() % 2 != 0) hex = "0" + hex;
        if ("89abcdef".indexOf(hex.charAt(0)) >= 0) hex = "00" + hex;
        return hex;
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] p : parts) length += p.length;
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
