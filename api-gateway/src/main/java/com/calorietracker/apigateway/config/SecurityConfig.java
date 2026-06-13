package com.calorietracker.apigateway.config;

import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Resource-server security for the API Gateway (reactive / WebFlux).
 *
 * The gateway is the public front door, so it performs coarse authentication:
 * everything under /api/** must carry a valid JWT. Fine-grained role checks are
 * delegated to backend-core. The incoming Authorization header is forwarded to
 * downstream services automatically by Spring Cloud Gateway.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Local profile: validate JWTs against a baked-in RSA public key, so the
     * stack runs and is verifiable offline. The 'cognito' profile instead uses
     * spring.security.oauth2.resourceserver.jwt.issuer-uri (auto-configured).
     */
    @Bean
    @Profile("localjwt")
    public ReactiveJwtDecoder localReactiveJwtDecoder(
            @Value("classpath:keys/local-public.pem") RSAPublicKey publicKey) {
        return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
    }
}
