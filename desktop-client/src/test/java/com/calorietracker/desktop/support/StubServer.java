package com.calorietracker.desktop.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local HTTP server that plays back scripted responses and records every request. */
public final class StubServer implements AutoCloseable {

    public record Request(String method, String uri, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0)).findFirst().orElse(null);
        }
    }

    public record Response(int status, String body) {}

    private final HttpServer server;
    private final ConcurrentLinkedDeque<Response> script = new ConcurrentLinkedDeque<>();
    public final List<Request> requests = new CopyOnWriteArrayList<>();

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(ex.getRequestMethod(), ex.getRequestURI().toString(), ex.getRequestHeaders(), body));
            Response r = script.isEmpty() ? new Response(500, "{\"message\":\"unscripted\"}") : script.poll();
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    /** Queue the next response (served in order). */
    public StubServer then(int status, String body) {
        script.add(new Response(status, body));
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public URI uri() {
        return URI.create(baseUrl() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
