package com.calorietracker.aiservice.ai;

/**
 * The upstream model provider (Gemini) failed or returned something
 * unusable. Mapped to 502 so clients can tell "the AI is down" apart from a
 * bug in this service. The message is logged, never returned to the caller.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiProviderException(String message) {
        super(message);
    }
}
