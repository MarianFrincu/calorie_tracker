package com.calorietracker.aiservice.ai;

/**
 * The AI provider is temporarily overloaded (both models tried). Mapped to
 * 503 + Retry-After with a "wait a few seconds" message: nothing is wrong on
 * our side and trying again shortly usually works.
 */
public class AiBusyException extends AiProviderException {

    public AiBusyException(String message) {
        super(message);
    }
}
