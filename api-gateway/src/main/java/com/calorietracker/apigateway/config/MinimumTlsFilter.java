package com.calorietracker.apigateway.config;

import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Refuses API calls whose browser-to-CloudFront connection used TLS older
 * than 1.2.
 *
 * <p>With the free {@code *.cloudfront.net} certificate CloudFront still
 * accepts TLS 1.0/1.1 from very old clients, and only a custom domain can
 * change that. CloudFront does tell the origin what was negotiated, in the
 * {@code CloudFront-Viewer-TLS} header (e.g. {@code TLSv1.3:TLS_AES_128_GCM_SHA256:fullHandshake}),
 * which CloudFront always sets itself - a client can't forge it. Requests
 * without the header (local runs, load-balancer health checks) pass.
 *
 * <p>Runs first, so a token sent over a weak connection is never even checked.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MinimumTlsFilter implements WebFilter {

    static final String VIEWER_TLS = "CloudFront-Viewer-TLS";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String tls = exchange.getRequest().getHeaders().getFirst(VIEWER_TLS);
        if (tls == null || tls.startsWith("TLSv1.2:") || tls.startsWith("TLSv1.3:")) {
            return chain.filter(exchange);
        }
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"status\":426,\"message\":\"Your browser's connection is too old (TLS 1.2 or newer is required).\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
