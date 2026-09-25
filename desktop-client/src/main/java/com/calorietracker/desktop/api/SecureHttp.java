package com.calorietracker.desktop.api;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Every HTTP client of the desktop app comes from here: TLS 1.2/1.3 only,
 * certificate and hostname checks against the JDK's trust store (no "trust
 * this certificate" switch), no redirects (a token is never replayed to
 * another host) and a connect timeout.
 */
public final class SecureHttp {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String[] PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    private SecureHttp() {}

    public static HttpClient newClient() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(PROTOCOLS);
        // Belt and braces: HttpClient already verifies hostnames, this makes the
        // requirement explicit and independent of the jdk.internal.* switch.
        params.setEndpointIdentificationAlgorithm("HTTPS");
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(sslContext())
                .sslParameters(params)
                .build();
    }

    /**
     * Refuses to send credentials over plain HTTP. In Cognito mode every API
     * call carries a bearer token, so {@code http://} is only acceptable to a
     * loopback address (a local stack on this machine).
     *
     * @throws IllegalArgumentException with a user-readable explanation
     */
    public static void requireSecureTransport(String baseUrl, boolean sendsCredentials) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("api.base.url is not a valid URL: " + baseUrl);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("api.base.url must start with https:// (got " + baseUrl + ")");
        }
        if (sendsCredentials && scheme.equals("http") && !isLoopback(uri.getHost())) {
            throw new IllegalArgumentException(
                    "Refusing to sign in over plain HTTP: your password-derived token would cross the "
                            + "network unencrypted. Use the https:// address of the server (api.base.url="
                            + baseUrl + ").");
        }
    }

    static boolean isLoopback(String host) {
        if (host == null) return false;
        if (host.equalsIgnoreCase("localhost")) return true;
        try {
            // Only literal addresses: never resolve an arbitrary name via DNS here.
            if (host.matches("[0-9.]+") || host.contains(":")) {
                return InetAddress.getByName(host).isLoopbackAddress();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static SSLContext sslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null); // JDK default key managers + trust store
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Could not set up TLS: " + e.getMessage(), e);
        }
    }
}
