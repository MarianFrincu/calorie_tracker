package com.calorietracker.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for the AI ingredient parser: a free-text meal description.
 * <p>
 * The size cap is a cost + DoS guard: with the Bedrock provider, every char
 * eventually becomes input tokens billed by the model. 2 KB is comfortably
 * larger than any realistic meal description.
 */
public record ParseRequest(
        @NotBlank(message = "text must not be blank")
        @Size(max = 2000, message = "text is too long (max 2000 characters)")
        String text) {
}
