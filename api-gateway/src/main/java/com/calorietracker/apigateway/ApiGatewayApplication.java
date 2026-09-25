package com.calorietracker.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway entry point.
 *
 * The single front door for both clients. It discovers backend services via
 * Eureka, forwards matching requests to them (see routes in application.yml),
 * validates every JWT at the edge and adds security headers to each response.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
