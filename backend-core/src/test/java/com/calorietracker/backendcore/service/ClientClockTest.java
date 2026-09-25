package com.calorietracker.backendcore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientClockTest {

    @Test
    void usesTheZoneTheClientSends() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(ClientClock.HEADER, "Europe/Bucharest");
        assertThat(ClientClock.parse(req)).isEqualTo(ZoneId.of("Europe/Bucharest"));
    }

    @Test
    void fallsBackToUtcForMissingOrBogusZones() {
        assertThat(ClientClock.parse(new MockHttpServletRequest())).isEqualTo(ZoneOffset.UTC);
        MockHttpServletRequest bogus = new MockHttpServletRequest();
        bogus.addHeader(ClientClock.HEADER, "Mars/Olympus_Mons");
        assertThat(ClientClock.parse(bogus)).isEqualTo(ZoneOffset.UTC);
        MockHttpServletRequest huge = new MockHttpServletRequest();
        huge.addHeader(ClientClock.HEADER, "x".repeat(500));
        assertThat(ClientClock.parse(huge)).isEqualTo(ZoneOffset.UTC);
    }
}
