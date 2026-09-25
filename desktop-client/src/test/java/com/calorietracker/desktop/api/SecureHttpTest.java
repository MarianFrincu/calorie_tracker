package com.calorietracker.desktop.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class SecureHttpTest {

    @Test
    void refusesPlainHttpToRemoteHostsWhenSendingTokens() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SecureHttp.requireSecureTransport("http://calorietracker-alb.eu-central-1.elb.amazonaws.com", true));
        assertTrue(e.getMessage().contains("plain HTTP"));
    }

    @Test
    void allowsHttpsAnywhereAndHttpToThisMachine() {
        assertDoesNotThrow(() -> SecureHttp.requireSecureTransport("https://api.example.com", true));
        assertDoesNotThrow(() -> SecureHttp.requireSecureTransport("http://localhost:8080", true));
        assertDoesNotThrow(() -> SecureHttp.requireSecureTransport("http://127.0.0.1:8080", true));
        assertDoesNotThrow(() -> SecureHttp.requireSecureTransport("http://[::1]:8080", true));
        // No credentials (dev backend without auth): plain HTTP is the user's call.
        assertDoesNotThrow(() -> SecureHttp.requireSecureTransport("http://192.168.1.10:8080", false));
    }

    @Test
    void rejectsNonHttpSchemesAndLookalikeHosts() {
        assertThrows(IllegalArgumentException.class, () -> SecureHttp.requireSecureTransport("ftp://example.com", false));
        // A name that merely starts with "localhost" is not loopback.
        assertThrows(IllegalArgumentException.class,
                () -> SecureHttp.requireSecureTransport("http://localhost.evil.example", true));
    }

    @Test
    void clientsOnlyOfferModernTlsAndVerifyHostnames() {
        HttpClient client = SecureHttp.newClient();
        assertArrayEquals(new String[]{"TLSv1.3", "TLSv1.2"}, client.sslParameters().getProtocols());
        assertEquals("HTTPS", client.sslParameters().getEndpointIdentificationAlgorithm());
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    }
}
