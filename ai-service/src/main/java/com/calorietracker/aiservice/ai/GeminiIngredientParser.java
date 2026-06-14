package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI parser backed by Google's Gemini API (https://aistudio.google.com).
 * Useful as a Bedrock fallback when the AWS account hits the daily-token
 * throttle or Bedrock access hasn't been granted in the deploy region.
 * <p>
 * Active when {@code calorietracker.ai.provider=gemini}. Reads the API key
 * from {@code GEMINI_API_KEY} (env var) and the model id from
 * {@code GEMINI_MODEL} (defaults to {@code gemini-2.0-flash}).
 * <p>
 * Implementation is plain HTTP + Jackson — no new dependencies. The prompts
 * mirror BedrockIngredientParser so swapping providers doesn't change the
 * structure of the AI output. Gemini's structured-JSON mode keeps the parse
 * trivial; we still strip optional markdown fences defensively because
 * different models occasionally wrap the JSON in {@code ```json ... ```}.
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "gemini")
public class GeminiIngredientParser implements IngredientParser {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

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
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GeminiIngredientParser(
            @Value("${calorietracker.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${calorietracker.ai.gemini.model:${GEMINI_MODEL:gemini-2.0-flash}}") String model) {
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
        this.mapper = new ObjectMapper();
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

    private <T> T callGemini(String systemPrompt, String userText, Class<T> type) {
        try {
            String body = mapper.writeValueAsString(Map.of(
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
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, model, apiKey)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException(
                        "Gemini API returned " + resp.statusCode() + " — model id wrong? key invalid? quota exceeded? " +
                        "First 300 chars of body: " + resp.body().substring(0, Math.min(300, resp.body().length())));
            }
            JsonNode root = mapper.readTree(resp.body());
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("Gemini returned an empty response (possibly safety-filtered).");
            }
            // Defensively strip ``` and ```json fences in case the model wraps its output.
            String stripped = text.trim();
            if (stripped.startsWith("```")) {
                stripped = stripped.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\\s*$", "");
            }
            return mapper.readValue(stripped, type);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Gemini call failed: " + e.getMessage(), e);
        }
    }
}
