package com.calorietracker.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.calorietracker.desktop.api.ApiException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

/** The copy users actually see when something goes wrong. */
class MessagesTest {

    @Test
    void prefersTheServersMessage() {
        assertEquals("amountGrams is required", Messages.friendly(new ApiException(400,
                "{\"status\":400,\"message\":\"amountGrams is required\"}")));
    }

    @Test
    void fallsBackToStatusCopy() {
        assertEquals("Your session has expired. Please sign in again.", Messages.friendly(new ApiException(401, "")));
        assertEquals("Too many requests. Please wait a moment and try again.", Messages.friendly(new ApiException(429, "")));
        assertEquals("The server had a problem. Please try again in a moment.", Messages.friendly(new ApiException(502, "<html>")));
    }

    @Test
    void networkProblemsGetActionableCopy() {
        assertEquals("Can't reach the server. Check your internet connection.",
                Messages.friendly(new RuntimeException(new UnknownHostException("x"))));
        assertEquals("The server isn't reachable right now. Is it running?",
                Messages.friendly(new ConnectException("refused")));
        assertEquals("The server took too long to respond. Please try again.",
                Messages.friendly(new HttpTimeoutException("slow")));
    }

    @Test
    void neverShowsJsonOrClassNames() {
        assertEquals("Something went wrong.", Messages.friendly(new RuntimeException("java.lang.NullPointerException")));
        assertEquals("Something went wrong.", Messages.friendly(new RuntimeException((String) null)));
    }
}
