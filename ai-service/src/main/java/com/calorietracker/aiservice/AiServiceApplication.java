package com.calorietracker.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stateless AI parser microservice.
 *
 * Exposes only the /api/ai/parse and /api/ai/parse-recipe endpoints. Persisting
 * AI-built recipes lives in backend-core (which owns the DB) under
 * POST /api/recipes/from-ai. This split keeps the (possibly paid, possibly
 * slow) text parsing isolated from the core API: it has its own rate limit
 * and can be scaled or swapped (library search / Gemini) independently.
 */
@SpringBootApplication
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
