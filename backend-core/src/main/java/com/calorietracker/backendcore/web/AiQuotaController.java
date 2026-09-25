package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.service.AiQuotaService;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service: ai-service calls this, with the user's own token,
 * before every AI parse. Not under /api, so the gateway never routes it and
 * it is unreachable from outside; the token still decides whose quota counts.
 */
@RestController
@RequestMapping("/internal/ai-quota")
public class AiQuotaController {

    private final AiQuotaService quota;

    public AiQuotaController(AiQuotaService quota) {
        this.quota = quota;
    }

    /** 204 = go ahead (the call is counted); 429 + Retry-After = over quota. */
    @PostMapping
    public ResponseEntity<Void> acquire() {
        Duration wait = quota.tryAcquire();
        if (wait.isZero()) {
            return ResponseEntity.noContent().build();
        }
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .build();
    }
}
