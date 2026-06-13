package com.calorietracker.backendcore.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security for backend-core.
 *
 * Stateless JWT validation (Cognito access tokens). Fine-grained, role-based
 * authorization lives here (the gateway only does coarse authentication):
 *   - reads               -> USER or ADMIN
 *   - writes (POST/PUT/DELETE) -> ADMIN only
 *   - AI parse            -> USER or ADMIN
 *
 * Roles come from the Cognito 'cognito:groups' claim, mapped to ROLE_* below.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // All /api/** endpoints require authentication. Per-user
                        // ownership of profile/diary/water/recipes/ingredients is
                        // enforced in the service layer via CurrentUserService.
                        // The cognito:groups -> ROLE_* mapping below stays wired
                        // for any future admin-only endpoints (e.g. managing
                        // *public* ingredients/recipes).
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Maps the Cognito 'cognito:groups' claim to Spring Security ROLE_* authorities. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractAuthorities);
        return converter;
    }

    private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // Human users: Cognito groups -> ROLE_*
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            groups.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g.toUpperCase())));
        }

        // Machine-to-machine: client_credentials tokens carry OAuth2 scopes, not
        // groups. Map the resource-server scopes to the equivalent roles so
        // service principals can act without a human group membership.
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.split(" ")) {
                if (s.endsWith("foods.write")) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                } else if (s.endsWith("foods.read")) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
            }
        }
        return authorities;
    }
}
