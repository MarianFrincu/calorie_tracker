package com.calorietracker.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.library.FoodLibrary;
import com.calorietracker.aiservice.ratelimit.AiQuota;
import com.calorietracker.aiservice.support.TestJwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** The default provider end to end over HTTP, with a stand-in for backend-core's library. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "calorietracker.ai.provider=library"
})
class AiLibraryModeIntegrationTest {

    @TestConfiguration
    static class FakeLibrary {
        @Bean
        @Primary
        FoodLibrary fakeFoodLibrary() {
            return name -> name.startsWith("egg")
                    ? Optional.of(new FoodLibrary.Food("Egg", 155, 13, 1.1, 11, 0))
                    : Optional.empty();
        }

        @Bean
        @Primary
        AiQuota unlimitedQuota() {
            return () -> { };
        }
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TestJwts.forUser("library-user"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Autowired
    ApplicationContext context;

    /**
     * Regression: a @LoadBalanced RestClient.Builder bean is also used by the
     * Eureka client for its own registry calls, which then fail - ai-service
     * never registered and the gateway answered 503 for every AI request.
     */
    @Test
    void noLoadBalancedRestClientBuilderBeanLeaksIntoTheEurekaClient() {
        assertThat(context.getBeansWithAnnotation(LoadBalanced.class).values())
                .noneMatch(bean -> bean instanceof RestClient.Builder);
    }

    @Test
    void diaryParseUsesLibraryValuesAndTotals() throws Exception {
        HttpResponse<String> r = post("/api/ai/parse", "{\"text\":\"2 eggs and space dust\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"name\":\"Egg\"", "\"calories\":155", "\"name\":\"Space dust\"")
                .contains("\"totalCalories\":255");
    }

    @Test
    void recipeParseReturnsPer100gLines() throws Exception {
        HttpResponse<String> r = post("/api/ai/parse-recipe", "{\"text\":\"egg 120g\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"kcalPer100g\":155", "\"amountGrams\":120.0");
    }
}
