package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI parser backed by Google's Gemini API (https://aistudio.google.com), which
 * has a free tier. Understands arbitrary dishes and portions, unlike the
 * library search.
 * <p>
 * Active when {@code calorietracker.ai.provider=gemini}. Reads the API key
 * from {@code GEMINI_API_KEY} (env var), the model from {@code GEMINI_MODEL}
 * (default {@code gemini-3.5-flash-lite}) and the model to switch to when that
 * one is overloaded from {@code GEMINI_FALLBACK_MODEL} (default
 * {@code gemini-3.1-flash-lite}; blank = retry the same model).
 * <p>
 * The key travels in the {@code x-goog-api-key} header, not the URL query
 * string, so it never lands in proxy logs or exception messages that echo the URI.
 * <p>
 * Implementation is plain HTTP + Jackson — no SDK. The requested JSON shapes
 * match what the other parsers return, so the clients can't tell providers
 * apart. Gemini's structured-JSON mode keeps the parse
 * trivial; we still strip optional markdown fences defensively because
 * different models occasionally wrap the JSON in {@code ```json ... ```}.
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "gemini")
public class GeminiIngredientParser implements IngredientParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiIngredientParser.class);

    /** {base}/v1beta/models/{model}:generateContent */
    private static final String API_PATH = "%s/v1beta/models/%s:generateContent";
    private static final Duration PAUSE = Duration.ofMillis(500);
    /** Statuses worth trying the other model for: overloaded, quota (per model), unknown model. */
    private static final Set<Integer> TRY_OTHER_MODEL = Set.of(404, 429, 500, 502, 503, 504);

    private static final String DIARY_PROMPT = """
            You are a nutrition expert. Split the user's meal description into
            individual ingredients. For each ingredient, estimate realistic
            nutrition for the stated portion (or a typical portion if none is
            given). Calories are in kcal; protein, carbs, fat and fiber are
            in grams. If a quantity is given, scale the values accordingly.
            Identify every ingredient you reasonably can. Always include a
            fiber estimate (0 for animal products and pure fats).

            Return ONLY a single JSON object with this exact shape, no prose,
            no markdown fences:
            {
              "items": [
                {
                  "name": "string",
                  "quantity": "string",
                  "calories": integer,
                  "protein": number,
                  "carbs": number,
                  "fat": number,
                  "fiber": number
                }
              ]
            }
            """;

    private static final String RECIPE_PROMPT = """
            You are a recipe assistant. The user describes a dish or a list
            of ingredients. Produce a structured recipe with:
              - a short, human-friendly name
              - a default servings value (1 unless the description clearly
                implies more)
              - a list of ingredients
            For each ingredient, give nutrition PER 100 GRAMS (kcal, protein,
            carbs, fat, fiber) AND the amount used in this recipe in grams.
            Always provide all fields. Always include a fiber estimate
            (0 for animal products and pure fats).

            Return ONLY a single JSON object with this exact shape, no prose,
            no markdown fences:
            {
              "name": "string",
              "servings": integer,
              "ingredients": [
                {
                  "name": "string",
                  "kcalPer100g": integer,
                  "proteinPer100g": number,
                  "carbsPer100g": number,
                  "fatPer100g": number,
                  "fiberPer100g": number,
                  "amountGrams": number
                }
              ]
            }
            """;

    private final String apiKey;
    private final String model;
    private final String fallbackModel;
    private final Duration primaryTimeout;
    private final Duration fallbackTimeout;
    private final HttpClient http;
    private final JsonMapper mapper;
    private final String baseUrl;

    @Autowired
    public GeminiIngredientParser(
            @Value("${calorietracker.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${calorietracker.ai.gemini.model:${GEMINI_MODEL:gemini-3.5-flash-lite}}") String model,
            @Value("${calorietracker.ai.gemini.fallback-model:${GEMINI_FALLBACK_MODEL:gemini-3.1-flash-lite}}") String fallbackModel,
            @Value("${calorietracker.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        // 15 s + pause + 25 s stays under the web app's 60 s AI timeout. The first
        // is short on purpose: a queued request usually means "try the other model".
        this(apiKey, model, fallbackModel, baseUrl, Duration.ofSeconds(15), Duration.ofSeconds(25));
    }

    GeminiIngredientParser(String apiKey, String model, String fallbackModel, String baseUrl,
                           Duration primaryTimeout, Duration fallbackTimeout) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.primaryTimeout = primaryTimeout;
        this.fallbackTimeout = fallbackTimeout;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY must be set when calorietracker.ai.provider=gemini. " +
                    "Get one at https://aistudio.google.com/app/apikey.");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // No fallback configured: the second attempt retries the same model.
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? model : fallbackModel;
        for (String m : List.of(model, this.fallbackModel)) {
            if (!m.matches("[A-Za-z0-9._-]+")) {
                // The model id is spliced into the URL path.
                throw new IllegalStateException("Invalid Gemini model id: " + m);
            }
        }
        // Model output is untrusted: tolerate extra fields and nulls in number
        // slots rather than failing the whole parse over one odd item.
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    @Override
    public List<ParsedIngredient> parse(String text) {
        LlmIngredientList result = callGemini(DIARY_PROMPT, text, LlmIngredientList.class);
        return result == null ? List.of() : result.items();
    }

    @Override
    public ParsedRecipe parseRecipe(String text) {
        LlmRecipe result = callGemini(RECIPE_PROMPT, text, LlmRecipe.class);
        if (result == null) return new ParsedRecipe("My recipe", 1, List.of());
        List<ParsedRecipeIngredient> ingredients = result.ingredients() == null ? List.of()
                : result.ingredients().stream()
                        .map(i -> new ParsedRecipeIngredient(
                                i.name(),
                                i.kcalPer100g(),
                                i.proteinPer100g(),
                                i.carbsPer100g(),
                                i.fatPer100g(),
                                i.fiberPer100g(),
                                i.amountGrams()))
                        .toList();
        return new ParsedRecipe(
                result.name() == null ? "My recipe" : result.name(),
                result.servings() == null ? 1 : result.servings(),
                ingredients);
    }

    /**
     * One call, at most two attempts: the configured model, then - if it is
     * overloaded, out of quota, slow or unknown - the fallback model, which has
     * its own capacity and free-tier quota. If both are busy the user gets a
     * clear "wait a few seconds" (AiBusyException, 503) instead of a failure.
     */
    private <T> T callGemini(String systemPrompt, String userText, Class<T> type) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userText))
                    )),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.2
                    )
            ));
        } catch (Exception e) {
            throw new AiProviderException("Could not build the Gemini request", e);
        }

        Attempt attempt = send(model, body, primaryTimeout);
        if (!attempt.ok() && attempt.worthTryingTheOtherModel()) {
            log.warn("Gemini model {} unavailable ({}); trying {}", model, attempt.describe(), fallbackModel);
            pause();
            attempt = send(fallbackModel, body, fallbackTimeout);
        }
        if (!attempt.ok()) {
            if (attempt.busy()) {
                throw new AiBusyException("Gemini is busy: " + attempt.describe());
            }
            throw new AiProviderException("Gemini API failed: " + attempt.describe());
        }
        return read(attempt.body(), type);
    }

    /** What one HTTP attempt produced: a status, or an I/O error / timeout (status 0). */
    private record Attempt(int status, String body, String error) {
        boolean ok() {
            return status == 200;
        }

        boolean busy() {
            return status == 0 || status == 500 || status == 502 || status == 503 || status == 504;
        }

        boolean worthTryingTheOtherModel() {
            return status == 0 || TRY_OTHER_MODEL.contains(status);
        }

        String describe() {
            return status == 0 ? error : "HTTP " + status + (error.isBlank() ? "" : ": " + error);
        }
    }

    private Attempt send(String modelId, String body, Duration timeout) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(String.format(API_PATH, baseUrl, modelId)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            // Google's own explanation, e.g. "API key not valid", "Quota exceeded",
            // "This model is currently experiencing high demand".
            String reason = resp.statusCode() == 200 ? "" : errorMessage(resp.body());
            return new Attempt(resp.statusCode(), resp.body(), reason);
        } catch (HttpTimeoutException e) {
            return new Attempt(0, "", "no answer within " + timeout.toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Gemini call interrupted", e);
        } catch (IOException e) {
            return new Attempt(0, "", "connection failed: " + e.getMessage());
        }
    }

    private String errorMessage(String body) {
        try {
            String message = mapper.readTree(body).path("error").path("message").asText("");
            return message.substring(0, Math.min(300, message.length()));
        } catch (Exception e) {
            return "";
        }
    }

    private <T> T read(String responseBody, Class<T> type) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText();
            if (text == null || text.isBlank()) {
                throw new AiProviderException("Gemini returned an empty response (possibly safety-filtered).");
            }
            // Defensively strip ``` and ```json fences in case the model wraps its output.
            String stripped = text.trim();
            if (stripped.startsWith("```")) {
                stripped = stripped.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\\s*$", "");
            }
            return mapper.readValue(stripped, type);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Gemini returned something that isn't the expected JSON: " + e.getMessage(), e);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Gemini call interrupted", e);
        }
    }
}
