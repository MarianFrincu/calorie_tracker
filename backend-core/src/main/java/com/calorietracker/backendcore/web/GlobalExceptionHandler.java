package com.calorietracker.backendcore.web;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.JacksonException;

/**
 * Translates exceptions into clean JSON error responses so both clients
 * always receive a predictable shape: {timestamp, status, error, message}.
 *
 * <p>Client mistakes (bad JSON, wrong types, unknown URLs, wrong methods) map
 * to 4xx. Only genuinely unexpected failures become 500, and those never echo
 * the exception text back.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldLabel(fe.getField()) + " " + fe.getDefaultMessage() + ".")
                .reduce((a, b) -> a + " " + b)
                .orElse("Some of the details aren't valid.");
        return build(HttpStatus.BAD_REQUEST, details);
    }

    /** Constraint annotations on controller method parameters (@RequestParam @Min ...). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        String details = ex.getAllErrors().stream()
                .map(e -> e.getDefaultMessage() == null ? "invalid value" : e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Unparseable body: broken JSON, a string where a number belongs, an unknown enum value... */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        String field = null;
        if (ex.getMostSpecificCause() instanceof JacksonException je && !je.getPath().isEmpty()) {
            field = je.getPath().get(je.getPath().size() - 1).getPropertyName();
        }
        return build(HttpStatus.BAD_REQUEST, field == null
                ? "Request body is missing or is not valid JSON."
                : "Invalid or missing value for '" + field + "'.");
    }

    /** Path variable or query parameter of the wrong type, e.g. /api/diary/abc or ?date=yesterday. */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(TypeMismatchException ex) {
        String name = ex.getPropertyName() == null ? "parameter" : "'" + ex.getPropertyName() + "'";
        return build(HttpStatus.BAD_REQUEST, "Invalid value for " + name + ".");
    }

    /** A write that the schema refuses (unique key, foreign key, not-null). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "That change conflicts with existing data.");
    }

    /** Service-level "won't do that" errors - e.g. deleting an ingredient still used by a recipe. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Bad domain input (e.g. AddDiaryRequest with no ingredient/recipe/customName). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Re-throw security exceptions so Spring Security's filter chain can render the right 401/403. */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void handleAuth(RuntimeException ex) {
        throw ex;
    }

    /**
     * Last-resort handler. Spring MVC's own exceptions (unknown URL, wrong HTTP
     * method, unsupported media type, missing parameter...) carry their status
     * via {@link ErrorResponse}; keep it rather than flattening them to 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAnyOther(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status == null) status = HttpStatus.BAD_REQUEST;
            HttpHeaders headers = new HttpHeaders();
            headers.addAll(er.getHeaders()); // e.g. Allow on 405
            return ResponseEntity.status(status).headers(headers).body(body(status, status.getReasonPhrase()));
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
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

    /**
     * A field path as the user knows it: "weightKg" -> "Weight (kg)",
     * "ingredients[0].amountGrams" -> "Amount (g)", "kcalPer100g" -> "Calories per 100 g".
     */
    static String fieldLabel(String path) {
        String field = path.substring(path.lastIndexOf('.') + 1).replaceAll("\\[\\d+]", "");
        String unit = "";
        for (String[] suffix : new String[][] {{"Kg", " (kg)"}, {"Cm", " (cm)"}, {"Ml", " (ml)"}, {"Grams", " (g)"}}) {
            if (field.endsWith(suffix[0]) && field.length() > suffix[0].length()) {
                field = field.substring(0, field.length() - suffix[0].length());
                unit = suffix[1];
                break;
            }
        }
        String words = field.replace("Per100g", " per 100 g").replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        words = words.replaceFirst("^kcal", "calories");
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + unit;
    }
}
