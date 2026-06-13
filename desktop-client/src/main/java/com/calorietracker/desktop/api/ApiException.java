package com.calorietracker.desktop.api;

/**
 * Raised when the backend returns a non-2xx status.
 * <p>
 * Carries the HTTP status and the raw response body so the UI layer can decide
 * how to surface it. The {@code Messages} helper in {@code ui} extracts a
 * friendly user-facing message from the body or falls back to a status-code
 * copywriter.
 */
public class ApiException extends RuntimeException {

    public final int status;
    public final String body;

    public ApiException(int status, String body) {
        super("HTTP " + status + (body == null || body.isBlank() ? "" : ": " + body));
        this.status = status;
        this.body = body;
    }
}
