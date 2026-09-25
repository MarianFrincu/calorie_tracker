package com.calorietracker.backendcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend-Core service.
 *
 * Owns the Calorie Tracker domain (CRUD over PostgreSQL). Registers itself
 * with Eureka so the API Gateway can route to it by logical name
 * ({@code lb://backend-core}).
 */
@SpringBootApplication
public class BackendCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendCoreApplication.class, args);
    }
}
