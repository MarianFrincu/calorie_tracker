package com.calorietracker.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway entry point.
 *
 * The single front door for the JavaFX client. It discovers backend services
 * via Eureka and forwards matching requests to them (see routes in
 * application.yml). Cognito JWT validation will be added here in a later step.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
