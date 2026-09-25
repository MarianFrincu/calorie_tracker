package com.calorietracker.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Security headers on every gateway response - errors (401/403) included, as
 * it runs before the security chain. HSTS only when the request arrived over
 * HTTPS, so a local HTTP run doesn't pin localhost. The CSP locks everything
 * down: the gateway only returns JSON.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersConfig implements WebFilter {

    /**
     * Only enable {@code includeSubDomains} + {@code preload} once you actually
     * serve every subdomain over HTTPS - preload in particular is hard to undo.
     */
    @Value("${calorietracker.security.hsts.max-age-seconds:31536000}")
    private long hstsMaxAge;

    @Value("${calorietracker.security.hsts.include-subdomains:false}")
    private boolean hstsIncludeSubdomains;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();

        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-site");
        // The API needs none of these device capabilities.
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        // JSON-only surface: deny everything, and never allow framing.
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        // Tokens and diary data must not be cached by intermediaries.
        headers.set("Cache-Control", "no-store");

        if (isSecure(exchange)) {
            headers.set("Strict-Transport-Security",
                    "max-age=" + hstsMaxAge + (hstsIncludeSubdomains ? "; includeSubDomains" : ""));
        }

        return chain.filter(exchange);
    }

    /**
     * True when the request reached the client over TLS. Behind the ALB the
     * connection into the container is plain HTTP, so the forwarded protocol
     * header is the only reliable signal.
     */
    private static boolean isSecure(ServerWebExchange exchange) {
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return exchange.getRequest().getURI().getScheme().equalsIgnoreCase("https");
    }
}
