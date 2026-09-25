package com.calorietracker.aiservice.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Runs the real HTTP client against a local stand-in for the Gemini API. */
class GeminiIngredientParserTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> keyHeader = new AtomicReference<>();

    private GeminiIngredientParser parserAnswering(int status, String modelText) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            String body = status != 200 ? "{\"error\":\"quota\"}"
                    : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + jsonEscape(modelText) + "\"}]}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new GeminiIngredientParser("test-key", "gemini-test", "",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsTheKeyInAHeaderAndParsesFencedJson() throws Exception {
        GeminiIngredientParser parser = parserAnswering(200, """
                ```json
                {"items":[{"name":"Egg","quantity":"2","calories":144,"protein":12.6,"carbs":0.8,"fat":9.6,"fiber":0,"extra":"ignored"}]}
                ```""");
        var items = parser.parse("2 eggs");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).calories()).isEqualTo(144);
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-test:generateContent");
        assertThat(path.get()).doesNotContain("key");
        assertThat(keyHeader.get()).isEqualTo("test-key");
    }

    @Test
    void parsesRecipes() throws Exception {
        GeminiIngredientParser parser = parserAnswering(200, """
                {"name":"Chicken rice","servings":2,"ingredients":[
                  {"name":"Chicken","kcalPer100g":165,"proteinPer100g":31,"carbsPer100g":0,"fatPer100g":3.6,"fiberPer100g":0,"amountGrams":200}]}""");
        var recipe = parser.parseRecipe("chicken and rice");
        assertThat(recipe.name()).isEqualTo("Chicken rice");
        assertThat(recipe.servings()).isEqualTo(2);
        assertThat(recipe.ingredients().get(0).amountGrams()).isEqualTo(200.0);
    }

    /** A stand-in Gemini whose answer depends on the model in the path: {status, delay ms}. */
    private GeminiIngredientParser scripted(Map<String, int[]> byModel, List<String> calledModels,
                                            Duration primaryTimeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String model = exchange.getRequestURI().getPath().replaceAll(".*/models/([^:]+):.*", "$1");
            calledModels.add(model);
            int[] answer = byModel.get(model);
            try {
                Thread.sleep(answer[1]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String body = answer[0] == 200
                    ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + jsonEscape("{\"items\":[]}") + "\"}]}}]}"
                    : "{\"error\":{\"code\":" + answer[0] + ",\"message\":\"This model is currently experiencing high demand.\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(answer[0], bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // the client gave up (timeout test)
            }
            exchange.close();
        });
        server.start();
        return new GeminiIngredientParser("k", "primary", "fallback",
                "http://127.0.0.1:" + server.getAddress().getPort(), primaryTimeout, Duration.ofSeconds(5));
    }

    @Test
    void overloadedModelSwitchesToTheFallbackModel() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        GeminiIngredientParser parser = scripted(Map.of("primary", new int[]{503, 0}, "fallback", new int[]{200, 0}),
                called, Duration.ofSeconds(5));
        assertThat(parser.parse("egg")).isEmpty();
        assertThat(called).containsExactly("primary", "fallback");
    }

    @Test
    void quotaOnOneModelSwitchesToTheOther() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        GeminiIngredientParser parser = scripted(Map.of("primary", new int[]{429, 0}, "fallback", new int[]{200, 0}),
                called, Duration.ofSeconds(5));
        assertThat(parser.parse("egg")).isEmpty();
        assertThat(called).containsExactly("primary", "fallback");
    }

    @Test
    void aSlowModelFailsOverInsteadOfHanging() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        GeminiIngredientParser parser = scripted(Map.of("primary", new int[]{200, 3000}, "fallback", new int[]{200, 0}),
                called, Duration.ofMillis(300));
        assertThat(parser.parse("egg")).isEmpty();
        assertThat(called).containsExactly("primary", "fallback");
    }

    @Test
    void bothModelsBusyIsAClearWaitAndRetry() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        GeminiIngredientParser parser = scripted(Map.of("primary", new int[]{503, 0}, "fallback", new int[]{503, 0}),
                called, Duration.ofSeconds(5));
        assertThatThrownBy(() -> parser.parse("egg"))
                .isInstanceOf(AiBusyException.class)
                .hasMessageContaining("high demand");
    }

    @Test
    void aBadRequestIsNotRetried() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        GeminiIngredientParser parser = scripted(Map.of("primary", new int[]{400, 0}, "fallback", new int[]{200, 0}),
                called, Duration.ofSeconds(5));
        assertThatThrownBy(() -> parser.parse("egg"))
                .isInstanceOf(AiProviderException.class)
                .isNotInstanceOf(AiBusyException.class);
        assertThat(called).containsExactly("primary");
    }

    @Test
    void providerErrorsBecomeAiProviderException() throws Exception {
        GeminiIngredientParser failing = parserAnswering(429, "");
        assertThatThrownBy(() -> failing.parse("egg")).isInstanceOf(AiProviderException.class);
    }

    @Test
    void emptyModelOutputIsAProviderError() throws Exception {
        GeminiIngredientParser empty = parserAnswering(200, "");
        assertThatThrownBy(() -> empty.parse("egg")).isInstanceOf(AiProviderException.class);
    }

    @Test
    void refusesToStartWithoutAKeyOrWithABadModelId() {
        assertThatThrownBy(() -> new GeminiIngredientParser("", "m", "", "http://x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GeminiIngredientParser("k", "../evil", "", "http://x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GeminiIngredientParser("k", "m", "../evil", "http://x"))
                .isInstanceOf(IllegalStateException.class);
    }
}
