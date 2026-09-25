package com.calorietracker.desktop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * Persists the Cognito session between launches ("stay signed in").
 *
 * <p>What is stored: the access token (valid 1 h) and the refresh token
 * (valid 30 days, revoked server-side on sign-out). Where: a properties file
 * in the user's config directory, readable and writable <b>by the owner
 * only</b>. The file is created with those permissions from the first byte
 * (POSIX mode 0600 / an owner-only ACL on Windows) and swapped in atomically,
 * so there is never a moment where another local user could read it.
 *
 * <p>This is the same protection the AWS CLI gives its credential cache. An
 * OS keychain would add encryption at rest; that needs native code per
 * platform and is noted as a follow-up in the README.
 */
public final class SessionStore {

    /** Mirrors RefreshTokenValidity in infra/cloudformation/03-cognito.yaml. */
    static final long REFRESH_LIFETIME_MS = 30L * 24 * 60 * 60 * 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Path file = Path.of(System.getProperty("user.home"), ".config", "calorie-tracker", "session.properties");

    /** What {@link #load()} hands back. {@code refreshToken} may be null for old sessions. */
    public record Saved(String accessToken, long accessExpiresAt, String refreshToken, long refreshExpiresAt) {
        public boolean accessValid(long now) {
            return accessToken != null && now < accessExpiresAt;
        }

        public boolean refreshValid(long now) {
            return refreshToken != null && now < refreshExpiresAt;
        }
    }

    private SessionStore() {}

    /** Test hook. */
    static void useFile(Path path) {
        file = path;
    }

    public static void save(CognitoAuthService.Tokens tokens, long refreshIssuedAt) {
        if (tokens == null || tokens.accessToken() == null) return;
        Properties p = new Properties();
        p.setProperty("accessToken", tokens.accessToken());
        p.setProperty("accessExpiresAt", String.valueOf(expiryOf(tokens.accessToken())));
        if (tokens.refreshToken() != null) {
            p.setProperty("refreshToken", tokens.refreshToken());
            p.setProperty("refreshExpiresAt", String.valueOf(refreshIssuedAt + REFRESH_LIFETIME_MS));
        }
        try {
            StringWriter out = new StringWriter();
            p.store(out, "Calorie Tracker session - owner-only; delete this file to sign out");
            writeOwnerOnly(out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Best effort: without persistence the user just signs in next launch.
        }
    }

    /** @return the saved session if either token is still usable, otherwise null (and the file is removed). */
    public static Saved load() {
        if (!Files.exists(file)) return null;
        try {
            Properties p = new Properties();
            p.load(new StringReader(Files.readString(file)));
            Saved s = new Saved(
                    blankToNull(p.getProperty("accessToken")),
                    Long.parseLong(p.getProperty("accessExpiresAt", "0")),
                    blankToNull(p.getProperty("refreshToken")),
                    Long.parseLong(p.getProperty("refreshExpiresAt", "0")));
            long now = System.currentTimeMillis();
            if (!s.accessValid(now) && !s.refreshValid(now)) {
                clear();
                return null;
            }
            return s;
        } catch (Exception e) {
            clear();
            return null;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // nothing sensible to do
        }
    }

    /** Epoch millis from the JWT's {@code exp} claim; "now + 30 min" if unreadable. */
    public static long expiryOf(String jwt) {
        try {
            String payload = jwt.split("\\.")[1];
            JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(payload));
            if (claims.path("exp").canConvertToLong()) {
                return claims.path("exp").asLong() * 1000L;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return System.currentTimeMillis() + 30L * 60 * 1000;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void writeOwnerOnly(byte[] content) throws IOException {
        Path dir = file.getParent();
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (!Files.exists(dir)) {
            if (posix) {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectories(dir);
            }
        }
        Path tmp = posix
                ? Files.createTempFile(dir, "session", ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(dir, "session", ".tmp");
        try {
            if (!posix) restrictToOwner(tmp);
            Files.write(tmp, content);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Windows: replace inherited ACL entries with a single owner-only entry. */
    private static void restrictToOwner(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (view == null) return;
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerOnly = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.of(
                        AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_ACL, AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE,
                        AclEntryPermission.SYNCHRONIZE))
                .build();
        view.setAcl(List.of(ownerOnly));
    }
}
