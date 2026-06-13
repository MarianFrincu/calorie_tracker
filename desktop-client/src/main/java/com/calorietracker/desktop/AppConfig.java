package com.calorietracker.desktop;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads configuration from classpath config.properties, overridable by JVM
 * system properties (-Dkey=value) or environment variables (KEY_WITH_UNDERSCORES).
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // fall back to defaults
        }
    }

    public String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null) {
            return sys;
        }
        String env = System.getenv(key.replace('.', '_').toUpperCase());
        if (env != null) {
            return env;
        }
        return props.getProperty(key, defaultValue);
    }

    public String apiBaseUrl()    { return get("api.base.url", "http://localhost:8080"); }
    public String authMode()      { return get("auth.mode", "none"); }   // none | cognito
    public String cognitoRegion() { return get("cognito.region", "us-east-1"); }
    public String cognitoClientId() { return get("cognito.client.id", ""); }
}
