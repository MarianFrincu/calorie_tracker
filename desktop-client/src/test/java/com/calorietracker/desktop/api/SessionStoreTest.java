package com.calorietracker.desktop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStoreTest {

    @TempDir
    Path dir;

    Path file;

    @BeforeEach
    void pointAtTempDir() {
        file = dir.resolve("nested").resolve("session.properties");
        SessionStore.useFile(file);
    }

    private static String jwtExpiringAt(long epochSeconds) {
        String payload = "{\"sub\":\"u\",\"exp\":" + epochSeconds + "}";
        return "h." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    @Test
    void roundTripsTokensAndReadsExpiryFromTheJwt() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        SessionStore.save(new CognitoAuthService.Tokens(jwtExpiringAt(exp), "refresh-1"), System.currentTimeMillis());
        SessionStore.Saved saved = SessionStore.load();
        assertNotNull(saved);
        assertEquals(exp * 1000, saved.accessExpiresAt());
        assertEquals("refresh-1", saved.refreshToken());
        assertTrue(saved.refreshValid(System.currentTimeMillis()));
    }

    @Test
    void fileAndDirectoryAreOwnerOnly() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        SessionStore.save(new CognitoAuthService.Tokens(jwtExpiringAt(9_999_999_999L), "r"), System.currentTimeMillis());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent())));
        try (var siblings = Files.list(file.getParent())) {
            assertEquals(1, siblings.count(), "temp file left behind");
        }
    }

    @Test
    void expiredSessionIsDiscarded() {
        long past = System.currentTimeMillis() / 1000 - 60;
        // No refresh token and an expired access token: nothing usable.
        SessionStore.save(new CognitoAuthService.Tokens(jwtExpiringAt(past), null), System.currentTimeMillis());
        assertNull(SessionStore.load());
        assertFalse(Files.exists(file));
    }

    @Test
    void expiredAccessTokenWithLiveRefreshTokenIsKept() {
        long past = System.currentTimeMillis() / 1000 - 60;
        SessionStore.save(new CognitoAuthService.Tokens(jwtExpiringAt(past), "refresh"), System.currentTimeMillis());
        SessionStore.Saved saved = SessionStore.load();
        assertNotNull(saved);
        assertFalse(saved.accessValid(System.currentTimeMillis()));
        assertTrue(saved.refreshValid(System.currentTimeMillis()));
    }

    @Test
    void refreshTokenOlderThanItsLifetimeIsDiscarded() {
        long past = System.currentTimeMillis() / 1000 - 60;
        long issuedLongAgo = System.currentTimeMillis() - SessionStore.REFRESH_LIFETIME_MS - 1000;
        SessionStore.save(new CognitoAuthService.Tokens(jwtExpiringAt(past), "refresh"), issuedLongAgo);
        assertNull(SessionStore.load());
    }
}
