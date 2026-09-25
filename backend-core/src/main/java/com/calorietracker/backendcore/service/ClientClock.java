package com.calorietracker.backendcore.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * "Today" as the caller sees it.
 *
 * <p>The server runs in UTC, but a diary day is the user's local day: someone
 * in Bucharest (UTC+3) who changes their objective at 01:00 means today, not
 * yesterday. Both clients send their IANA zone in {@value #HEADER}; this falls
 * back to UTC when the header is missing or not a real zone id.
 */
@Component
public class ClientClock {

    public static final String HEADER = "X-Time-Zone";

    private final Clock clock;

    public ClientClock() {
        this(Clock.systemUTC());
    }

    ClientClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    public ZoneId zone() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return ZoneOffset.UTC;
        }
        return parse(attrs.getRequest());
    }

    static ZoneId parse(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        // Longest real IANA ids are ~32 chars; anything much longer is junk.
        if (raw == null || raw.isBlank() || raw.length() > 64) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
