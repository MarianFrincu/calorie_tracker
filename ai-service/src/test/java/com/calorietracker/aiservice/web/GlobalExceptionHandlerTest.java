package com.calorietracker.aiservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.ai.AiBusyException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void aBusyProviderTellsTheUserToWaitAFewSeconds() {
        var response = new GlobalExceptionHandler().handleBusy(new AiBusyException("both models: HTTP 503"));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("10");
        assertThat(String.valueOf(response.getBody().get("message"))).contains("wait a few seconds");
    }
}
