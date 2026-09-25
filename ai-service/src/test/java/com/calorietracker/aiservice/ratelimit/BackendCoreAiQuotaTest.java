package com.calorietracker.aiservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.calorietracker.aiservice.ai.AiProviderException;
import com.calorietracker.aiservice.config.BackendCoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class BackendCoreAiQuotaTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://backend-core")
            .requestInterceptor(BackendCoreConfig.forwardAuthorization());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BackendCoreAiQuota quota = new BackendCoreAiQuota(builder.build());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowedCallsSpendTheCallersOwnQuota() {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        server.expect(requestTo("http://backend-core/internal/ai-quota"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withNoContent());

        quota.consume();
        server.verify();
    }

    @Test
    void overQuotaCarriesTheRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "17");
        server.expect(requestTo("http://backend-core/internal/ai-quota"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(quota::consume)
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(17));
    }

    @Test
    void failsClosedWhenTheQuotaCantBeChecked() {
        server.expect(requestTo("http://backend-core/internal/ai-quota")).andRespond(withServerError());

        assertThatThrownBy(quota::consume).isInstanceOf(AiProviderException.class);
    }
}
