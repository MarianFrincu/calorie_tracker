package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ugly exceptions into one-sentence English messages suitable for an
 * end-user alert. The goal is to never show a raw JSON body, a stack trace,
 * or a Java exception class name to the user.
 *
 * <p>Strategy:
 * <ol>
 *   <li>If the cause is an {@link ApiException}, prefer the parsed {@code message}
 *       field from the backend's error JSON. Otherwise fall back to a polite
 *       sentence based on the HTTP status code.</li>
 *   <li>For network errors (connect refused, DNS, timeouts) use friendly,
 *       action-oriented copy.</li>
 *   <li>For anything else, use the exception's {@code getMessage()} if it
 *       looks human (short, no JSON, no class names), otherwise a generic
 *       "Something went wrong" line.</li>
 * </ol>
 */
public final class Messages {

    /** "message" field in our backend's standard error envelope {timestamp,status,error,message}. */
    private static final Pattern JSON_MESSAGE = Pattern.compile(
            "\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private Messages() {}

    public static String friendly(Throwable t) {
        if (t == null) return "Something went wrong.";

        // Walk the chain a bit; the real cause is often wrapped.
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof ApiException ae) return apiMessage(ae);

            if (cur instanceof UnknownHostException) {
                return "Can't reach the server. Check your internet connection.";
            }
            if (cur instanceof ConnectException) {
                return "The server isn't reachable right now. Is it running?";
            }
            if (cur instanceof HttpConnectTimeoutException
                    || cur instanceof HttpTimeoutException
                    || cur instanceof SocketTimeoutException) {
                return "The server took too long to respond. Please try again.";
            }
            if (cur == t.getCause()) {
                // Avoid walking forever on circular causes.
            }
        }

        // Last resort: trust the exception's own message only if it's short,
        // free of JSON, and free of Java class names.
        String raw = t.getMessage();
        if (raw == null || raw.isBlank()) return "Something went wrong.";
        String cleaned = stripJson(raw);
        if (cleaned == null || looksLikeJunk(cleaned)) return "Something went wrong.";
        return cleaned.trim();
    }

    /** Public so other callers (login flow, etc.) can clean Cognito error bodies too. */
    public static String apiMessage(ApiException ae) {
        // Prefer a parsed body message; otherwise use a status-code copywriter.
        String fromBody = extractMessageField(ae.body);
        if (fromBody != null && !fromBody.isBlank()) return fromBody;
        return statusCopy(ae.status);
    }

    /** Pull "message":"..." out of the backend's error JSON. Tolerates embedded escapes. */
    public static String extractMessageField(String body) {
        if (body == null || body.isBlank()) return null;
        // Cheap reject: if there's no { at all, it's not JSON we care about.
        if (body.indexOf('{') < 0) return null;
        Matcher m = JSON_MESSAGE.matcher(body);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\n", " ").trim();
    }

    /** Friendly sentence per HTTP status family. */
    public static String statusCopy(int status) {
        return switch (status) {
            case 400 -> "The request looks wrong. Double-check the values and try again.";
            case 401 -> "Your session has expired. Please sign in again.";
            case 403 -> "You don't have permission to do that.";
            case 404 -> "We couldn't find what you were looking for.";
            case 408, 504 -> "The server took too long to respond. Please try again.";
            case 409 -> "That can't be done right now (the data is in conflict).";
            case 429 -> "Too many requests. Please wait a moment and try again.";
            case 500, 502, 503 -> "The server had a problem. Please try again in a moment.";
            default -> "Something went wrong (HTTP " + status + ").";
        };
    }

    /** Drops a JSON object literal from a message like "HTTP 400: {\"message\":...}". */
    private static String stripJson(String raw) {
        int brace = raw.indexOf('{');
        if (brace < 0) return raw;
        String prefix = raw.substring(0, brace).trim();
        // Try to recover a "message" field from the JSON part anyway.
        String parsed = extractMessageField(raw.substring(brace));
        if (parsed != null && !parsed.isBlank()) return parsed;
        if (prefix.isBlank()) return null;
        // Drop trailing punctuation like "HTTP 400:".
        return prefix.replaceAll("[:\\-,\\s]+$", "");
    }

    /** Heuristic for "this isn't a human sentence". */
    private static boolean looksLikeJunk(String s) {
        if (s == null) return true;
        // Likely an exception class name (e.g. "java.net.SocketException")
        if (s.contains(".") && s.contains("Exception")) return true;
        // Still contains JSON syntax
        if (s.contains("{\"") || s.contains("\":")) return true;
        // Too long for a one-liner
        return s.length() > 220;
    }
}
