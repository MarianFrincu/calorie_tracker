package com.calorietracker.aiservice.ratelimit;

import com.calorietracker.aiservice.ai.AiProviderException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link AiQuota} kept by backend-core ({@code POST /internal/ai-quota}), in
 * PostgreSQL - so the limit is exact however many ai-service tasks run. The
 * call carries the user's own token, so it can only ever spend their quota.
 *
 * <p>Fails closed: if the quota can't be checked, no AI call is made. An
 * unmetered AI call is exactly what the quota exists to prevent.
 */
public class BackendCoreAiQuota implements AiQuota {

    private final RestClient client;

    public BackendCoreAiQuota(RestClient client) {
        this.client = client;
    }

    @Override
    public void consume() {
        HttpStatus status;
        String retryAfter;
        try {
            var response = client.post().uri("/internal/ai-quota")
                    .retrieve()
                    .onStatus(s -> s.value() == 429, (req, res) -> { })
                    .toBodilessEntity();
            status = HttpStatus.valueOf(response.getStatusCode().value());
            retryAfter = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        } catch (RestClientException e) {
            throw new AiProviderException("AI quota check failed: " + e.getMessage(), e);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            throw new RateLimitExceededException(Duration.ofSeconds(parseSeconds(retryAfter)));
        }
    }

    private static long parseSeconds(String retryAfter) {
        try {
            return retryAfter == null ? 60 : Math.max(1, Long.parseLong(retryAfter.trim()));
        } catch (NumberFormatException e) {
            return 60;
        }
    }
}
