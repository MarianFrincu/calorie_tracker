package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real AI parser backed by Spring AI + AWS Bedrock (Converse API). Active only
 * when {@code calorietracker.ai.provider=bedrock} (also requires the bedrock
 * Spring profile so the chat model + region are auto-configured).
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "bedrock")
public class BedrockIngredientParser implements IngredientParser {

    private static final String DIARY_PROMPT = """
            You are a nutrition expert. Split the user's meal description into
            individual ingredients. For each ingredient, estimate realistic
            nutrition for the stated portion (or a typical portion if none is
            given). Calories are in kcal; protein, carbs, fat and fiber are
            in grams. If a quantity is given, scale the values accordingly.
            Identify every ingredient you reasonably can. Always include a
            fiber estimate (0 for animal products and pure fats).
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
            """;

    private final ChatClient chatClient;

    public BedrockIngredientParser(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<ParsedIngredient> parse(String text) {
        LlmIngredientList result = chatClient.prompt()
                .system(DIARY_PROMPT)
                .user(text)
                .call()
                .entity(LlmIngredientList.class);
        return result == null ? List.of() : result.items();
    }

    @Override
    public ParsedRecipe parseRecipe(String text) {
        LlmRecipe result = chatClient.prompt()
                .system(RECIPE_PROMPT)
                .user(text)
                .call()
                .entity(LlmRecipe.class);
        if (result == null) return new ParsedRecipe("My recipe", 1, List.of());
        List<ParsedRecipeIngredient> ingredients = result.ingredients() == null ? List.of()
                : result.ingredients().stream()
                        .map(i -> new ParsedRecipeIngredient(
                                i.name(), i.kcalPer100g(),
                                i.proteinPer100g(), i.carbsPer100g(),
                                i.fatPer100g(), i.fiberPer100g(),
                                i.amountGrams()))
                        .toList();
        return new ParsedRecipe(
                result.name() == null ? "My recipe" : result.name(),
                result.servings() == null ? 1 : result.servings(),
                ingredients);
    }
}
