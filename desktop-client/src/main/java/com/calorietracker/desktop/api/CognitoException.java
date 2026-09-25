package com.calorietracker.desktop.api;

import java.util.Locale;

/**
 * A Cognito failure whose message is fit to show the user as-is (no codes, no
 * jargon). {@link #type()} is Cognito's error name, for code that reacts to a
 * specific case (e.g. UserNotConfirmedException → the verify-code step).
 * The same sentences as the web client's cognito.ts.
 */
public class CognitoException extends RuntimeException {

    private final String type;

    public CognitoException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }

    /** Cognito's error → what the user should read. */
    public static String friendly(String type, String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return switch (type == null ? "" : type) {
            case "NotAuthorizedException" -> {
                if (m.contains("attempts exceeded")) yield "Too many failed attempts. Wait a few minutes and try again.";
                if (m.contains("disabled")) yield "This account is disabled.";
                if (m.contains("refresh token") || m.contains("revoked") || m.contains("expired")) {
                    yield "Your session expired. Please sign in again.";
                }
                yield "Incorrect email or password.";
            }
            case "UserNotFoundException" -> "Incorrect email or password.";
            case "UserNotConfirmedException" -> "Your email isn't confirmed yet. Enter the code we emailed you.";
            case "UsernameExistsException" -> "An account with this email already exists. Sign in, or use \"Forgot password?\".";
            case "InvalidPasswordException" -> "The password doesn't meet the rules: at least 12 characters, "
                    + "with upper- and lowercase letters and a number.";
            case "CodeMismatchException" -> "That code isn't right. Check the email and try again.";
            case "ExpiredCodeException" -> "That code has expired. Ask for a new one.";
            case "LimitExceededException", "TooManyRequestsException", "TooManyFailedAttemptsException" ->
                    "Too many attempts. Please wait a few minutes and try again.";
            case "InvalidParameterException" -> m.contains("email") || m.contains("username")
                    ? "Please enter a valid email address." : "Some of the details aren't valid.";
            default -> "Something went wrong. Please try again.";
        };
    }
}
