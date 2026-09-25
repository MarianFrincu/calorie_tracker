package com.calorietracker.aiservice.web;

import com.calorietracker.aiservice.ai.AiBusyException;
import com.calorietracker.aiservice.ai.AiProviderException;
import com.calorietracker.aiservice.ratelimit.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mirror of backend-core's handler. Ensures provider failures never
 * surface as Spring's whitelabel error (which leaks the underlying exception
 * class name to the client). Client mistakes map to 4xx, provider outages to
 * 502, everything else to a bare 500; security exceptions are re-thrown so
 * the filter chain renders 401/403 itself.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AiBusyException.class)
    public ResponseEntity<Map<String, Object>> handleBusy(AiBusyException ex) {
        log.warn("AI provider busy: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(body(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI is very busy right now (high demand at Google). Please wait a few seconds and try again."));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<Map<String, Object>> handleProvider(AiProviderException ex) {
        log.warn("AI provider failure: {}", ex.getMessage(), ex.getCause());
        return build(HttpStatus.BAD_GATEWAY, "The AI service is unavailable right now. Please try again in a moment.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        long seconds = ex.retryAfterSeconds();
        String wait = seconds < 90 ? seconds + " seconds"
                : seconds < 5400 ? (seconds / 60) + " minutes" : (seconds / 3600) + " hours";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, "AI limit reached. Try again in " + wait + "."));
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void handleAuth(RuntimeException ex) {
        throw ex;
    }

    /** Last resort; keeps the status of Spring MVC's own exceptions (404, 405, 415...). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAnyOther(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status == null) status = HttpStatus.BAD_REQUEST;
            HttpHeaders headers = new HttpHeaders();
            headers.addAll(er.getHeaders());
            return ResponseEntity.status(status).headers(headers).body(body(status, status.getReasonPhrase()));
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(body(status, message));
    }

    private static Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
