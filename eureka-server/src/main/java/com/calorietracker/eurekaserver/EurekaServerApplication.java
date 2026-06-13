package com.calorietracker.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Registry entry point.
 *
 * {@code @EnableEurekaServer} turns this plain Spring Boot app into a Eureka
 * registry. Every other microservice (Gateway, Backend-Core) will register
 * here on startup so they can discover each other by logical name instead of
 * hard-coded host/port.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
