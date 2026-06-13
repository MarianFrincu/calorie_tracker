package com.calorietracker.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * LOCAL DEVELOPMENT ONLY ('dev' profile): the gateway lets all traffic through
 * so the desktop client works without tokens. Real security ({@link SecurityConfig})
 * is active for every other profile.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityWebFilterChain devSecurityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll());
        return http.build();
    }
}
