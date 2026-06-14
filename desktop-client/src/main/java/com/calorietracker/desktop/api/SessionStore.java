package com.calorietracker.desktop.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists the Cognito access token between JVM launches so the user stays
 * signed in for up to 30 minutes without re-entering credentials. Stored as
 * a plain properties file in the user's config directory; gets cleared on
 * sign-out and when the saved timestamp is older than 30 minutes.
 */
public final class SessionStore {

    private static final Path FILE = Path.of(
            System.getProperty("user.home"), ".config", "calorie-tracker", "session.properties");
    private static final long MAX_AGE_MS = 30L * 60L * 1000L;

    private SessionStore() {}

    public static void save(String token) {
        if (token == null || token.isBlank()) return;
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("token", token);
            p.setProperty("savedAt", String.valueOf(System.currentTimeMillis()));
            try (BufferedWriter w = Files.newBufferedWriter(FILE)) {
                p.store(w, "Calorie Tracker session — auto-saved, expires after 30 min");
            }
            FILE.toFile().setReadable(false, false);
            FILE.toFile().setReadable(true, true);
            FILE.toFile().setWritable(false, false);
            FILE.toFile().setWritable(true, true);
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }

    /** @return a token if the saved one is still within the 30-min window; {@code null} otherwise. */
    public static String loadIfValid() {
        if (!Files.exists(FILE)) return null;
        try (BufferedReader r = Files.newBufferedReader(FILE)) {
            Properties p = new Properties();
            p.load(r);
            long savedAt = Long.parseLong(p.getProperty("savedAt", "0"));
            if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) {
                clear();
                return null;
            }
            String token = p.getProperty("token", "").trim();
            return token.isEmpty() ? null : token;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        try { Files.deleteIfExists(FILE); } catch (IOException ignored) {}
    }
}
