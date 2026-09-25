package com.calorietracker.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Resource-server security for the API Gateway (reactive / WebFlux).
 *
 * The gateway is the public front door, so it performs coarse authentication:
 * everything under /api/** must carry a valid JWT. Fine-grained role checks are
 * delegated to backend-core. The incoming Authorization header is forwarded to
 * downstream services automatically by Spring Cloud Gateway.
 *
 * The JWT decoder is auto-configured from the 'cognito' profile (the user
 * pool's issuer-uri) and hardened by {@link CognitoAccessTokenValidator}.
 *
 * No CORS configuration on purpose: the web app is served from the same origin
 * as /api, so browsers never need cross-origin access and none is granted.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                // No cookies are used - the client sends a bearer token - so there is
                // no ambient authority for a cross-site request to ride on and CSRF
                // protection has nothing to protect.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // Health checks: the ALB and Docker healthchecks are unauthenticated.
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
