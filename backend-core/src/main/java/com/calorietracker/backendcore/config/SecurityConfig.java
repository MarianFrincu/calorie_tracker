package com.calorietracker.backendcore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security for backend-core: stateless validation of Cognito
 * access tokens (see {@link CognitoAccessTokenValidator}). The gateway checks
 * the token too; this is defense in depth for anything that reaches the
 * service directly.
 *
 * <p>There are no roles: every signed-in user has the same rights, over their
 * own data only. That ownership is enforced in the service layer - each query
 * is scoped to the caller via CurrentUserService - and the shared food library
 * is read-only through the API.
 */
@Configuration
@EnableWebSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
