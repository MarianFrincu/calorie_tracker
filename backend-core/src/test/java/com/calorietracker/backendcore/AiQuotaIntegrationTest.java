package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.backendcore.support.TestJwts;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** The shared AI quota (5 per minute, 8 per day in tests - see IntegrationTest). */
class AiQuotaIntegrationTest extends IntegrationTest {

    private HttpResponse<String> acquire(String token) throws Exception {
        return post("/internal/ai-quota", token, "");
    }

    /** The minute window resets on the clock; don't start a test just before it does. */
    private static void awayFromMinuteBoundary() throws InterruptedException {
        int second = Instant.now().atZone(ZoneOffset.UTC).getSecond();
        if (second > 50) {
            Thread.sleep((61 - second) * 1000L);
        }
    }

    @Test
    void countsPerMinuteThenPerDayAndTellsWhenToRetry() throws Exception {
        awayFromMinuteBoundary();
        String t = newUser();
        for (int i = 0; i < 5; i++) {
            assertThat(acquire(t).statusCode()).isEqualTo(204);
        }
        HttpResponse<String> limited = acquire(t);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 60L);

        // A new minute: the burst window resets, the daily count carries on (5 of 8 used).
        long userId = jdbc.queryForObject("SELECT id FROM app_users WHERE user_key = ?", Long.class,
                TestJwts.subjectOf(t));
        jdbc.update("UPDATE ai_usage SET window_start = window_start - interval '1 minute' "
                + "WHERE user_id = ? AND window_kind = 'MINUTE'", userId);
        for (int i = 0; i < 3; i++) {
            assertThat(acquire(t).statusCode()).isEqualTo(204);
        }
        HttpResponse<String> daily = acquire(t);
        assertThat(daily.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(daily.headers().firstValue("Retry-After").orElseThrow())).isGreaterThan(0L);

        // Quotas are per user.
        assertThat(acquire(newUser()).statusCode()).isEqualTo(204);

        // Deleting the account removes its counters with it.
        assertThat(delete("/api/profile", t).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void isExactUnderConcurrentCalls() throws Exception {
        awayFromMinuteBoundary();
        String t = newUser();
        acquire(t); // provision the user first, so the race below is only about the quota
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(() -> acquire(t).statusCode()));
            }
        }
        int allowed = 0;
        for (Future<Integer> r : results) {
            if (r.get() == 204) allowed++;
        }
        assertThat(allowed).isEqualTo(4); // 5 per minute, one already used
    }

    @Test
    void needsAToken() throws Exception {
        assertThat(acquire(null).statusCode()).isEqualTo(401);
    }
}
