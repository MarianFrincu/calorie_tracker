package com.calorietracker.backendcore.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user quota on AI calls: a short burst limit plus a daily cap (UTC days).
 *
 * <p>Gemini calls cost money (or free-tier quota) and library lookups cost
 * database work, and sign-up is open: without this any account could run up
 * the bill with a loop. ai-service asks here before every parse. The counters
 * live in PostgreSQL, so the limit is exact however many ai-service tasks run.
 *
 * <p>Both windows are checked and counted in one transaction under row locks,
 * so concurrent calls from the same user can't slip past the limit. A
 * rejected call is not counted. Fixed windows are deliberately simple: they
 * allow at most 2x the burst limit across a minute boundary - fine for a
 * cost guard.
 */
@Service
public class AiQuotaService {

    private static final String MINUTE = "MINUTE";
    private static final String DAY = "DAY";

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;
    private final int perMinute;
    private final int perDay;
    private final Clock clock;

    @Autowired
    public AiQuotaService(JdbcTemplate jdbc, CurrentUserService currentUser,
                          @Value("${calorietracker.ai.rate-limit.per-minute:10}") int perMinute,
                          @Value("${calorietracker.ai.rate-limit.per-day:200}") int perDay) {
        this(jdbc, currentUser, perMinute, perDay, Clock.systemUTC());
    }

    AiQuotaService(JdbcTemplate jdbc, CurrentUserService currentUser, int perMinute, int perDay, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    /**
     * Counts one AI call for the current user, if the quota allows it.
     *
     * @return {@link Duration#ZERO} when allowed, otherwise how long until the
     *         user may try again (for the Retry-After header)
     */
    @Transactional
    public Duration tryAcquire() {
        long userId = currentUser.current().getId();
        Instant now = clock.instant();
        Timestamp minute = Timestamp.from(now.truncatedTo(ChronoUnit.MINUTES));
        Timestamp day = Timestamp.from(now.truncatedTo(ChronoUnit.DAYS));

        // Housekeeping: this user's finished windows are never read again.
        jdbc.update("""
                DELETE FROM ai_usage WHERE user_id = ?
                  AND ((window_kind = 'MINUTE' AND window_start < ?) OR (window_kind = 'DAY' AND window_start < ?))
                """, userId, minute, day);
        jdbc.update("""
                INSERT INTO ai_usage (user_id, window_kind, window_start, calls)
                VALUES (?, 'MINUTE', ?, 0), (?, 'DAY', ?, 0)
                ON CONFLICT DO NOTHING
                """, userId, minute, userId, day);
        // FOR UPDATE: a concurrent call for the same user waits here until
        // this one has counted itself (or been rejected).
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT window_kind, calls FROM ai_usage WHERE user_id = ?
                  AND ((window_kind = 'MINUTE' AND window_start = ?) OR (window_kind = 'DAY' AND window_start = ?))
                FOR UPDATE
                """, userId, minute, day);
        int minuteCalls = 0;
        int dayCalls = 0;
        for (Map<String, Object> row : rows) {
            int calls = ((Number) row.get("calls")).intValue();
            if (MINUTE.equals(row.get("window_kind"))) minuteCalls = calls;
            if (DAY.equals(row.get("window_kind"))) dayCalls = calls;
        }

        if (dayCalls >= perDay) {
            return Duration.between(now, day.toInstant().plus(1, ChronoUnit.DAYS));
        }
        if (minuteCalls >= perMinute) {
            return Duration.between(now, minute.toInstant().plus(1, ChronoUnit.MINUTES));
        }
        jdbc.update("""
                UPDATE ai_usage SET calls = calls + 1 WHERE user_id = ?
                  AND ((window_kind = 'MINUTE' AND window_start = ?) OR (window_kind = 'DAY' AND window_start = ?))
                """, userId, minute, day);
        return Duration.ZERO;
    }
}
