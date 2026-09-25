package com.calorietracker.aiservice.config;

import com.calorietracker.aiservice.library.BackendCoreFoodLibrary;
import com.calorietracker.aiservice.library.FoodLibrary;
import com.calorietracker.aiservice.ratelimit.AiQuota;
import com.calorietracker.aiservice.ratelimit.BackendCoreAiQuota;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The one HTTP client ai-service uses to call backend-core (found through
 * Eureka), always on behalf of - and with the token of - the user whose
 * request is being served. Used for the AI quota and, with provider=library,
 * the food library.
 *
 * <p>Deliberately NOT a {@code @LoadBalanced RestClient.Builder} bean: the
 * Eureka client picks up any {@code RestClient.Builder} bean for its own
 * registry calls, and a load-balanced one makes it try to resolve
 * "eureka-server" as a service - so ai-service never registers and every
 * /api/ai call fails with 503. Resolution happens in a private interceptor
 * instead, and only for the backend-core host.
 */
@Configuration
public class BackendCoreConfig {

    private final RestClient client;

    public BackendCoreConfig(@Value("${calorietracker.ai.library.base-url:http://backend-core}") String baseUrl,
                             @Value("${calorietracker.ai.library.service-id:backend-core}") String serviceId,
                             ObjectProvider<LoadBalancerClient> loadBalancer) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        // Each call is one or two indexed queries; a slow answer means something is wrong.
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(forwardAuthorization())
                .requestInterceptor(resolveService(serviceId, loadBalancer))
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    AiQuota aiQuota() {
        return new BackendCoreAiQuota(client);
    }

    @Bean
    @ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "library", matchIfMissing = true)
    FoodLibrary foodLibrary() {
        return new BackendCoreFoodLibrary(client);
    }

    /** Copies the incoming request's Authorization header: the call is made on the user's behalf. */
    public static ClientHttpRequestInterceptor forwardAuthorization() {
        return (request, body, execution) -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null) request.getHeaders().set(HttpHeaders.AUTHORIZATION, auth);
            }
            return execution.execute(request, body);
        };
    }

    /** Rewrites http://{serviceId}/... to a live instance's address; other hosts pass through. */
    static ClientHttpRequestInterceptor resolveService(String serviceId, ObjectProvider<LoadBalancerClient> lbProvider) {
        return (request, body, execution) -> {
            if (!serviceId.equalsIgnoreCase(request.getURI().getHost())) {
                return execution.execute(request, body);
            }
            LoadBalancerClient lb = lbProvider.getIfAvailable();
            ServiceInstance instance = lb == null ? null : lb.choose(serviceId);
            if (instance == null) {
                throw new IOException("No instance of " + serviceId + " is registered yet");
            }
            URI target = lb.reconstructURI(instance, request.getURI());
            HttpRequest resolved = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return target;
                }
            };
            return execution.execute(resolved, body);
        };
    }
}
