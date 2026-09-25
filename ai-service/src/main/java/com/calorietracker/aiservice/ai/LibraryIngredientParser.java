package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import com.calorietracker.aiservice.library.FoodLibrary;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Free, no-external-API parser: every item in the text is looked up in the
 * app's food library (public foods + the user's own), and its per-100 g values
 * are scaled to the amount written.
 *
 * <p>Amounts: an explicit weight ("150g rice") is used as is; a count ("2
 * eggs") is multiplied by a typical piece weight (egg 50 g, banana 118 g...,
 * 100 g when unknown). Foods the library doesn't know fall back to the
 * built-in table, so a result always comes back. This is the default provider.
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "library", matchIfMissing = true)
public class LibraryIngredientParser implements IngredientParser {

    private final FoodLibrary library;

    public LibraryIngredientParser(FoodLibrary library) {
        this.library = library;
    }

    @Override
    public List<ParsedIngredient> parse(String text) {
        return MealText.split(text).stream().map(item -> {
            Optional<FoodLibrary.Food> food = library.find(item.name());
            if (food.isEmpty()) return MockIngredientParser.estimate(item);
            FoodLibrary.Food f = food.get();
            double factor = gramsOf(item) / 100.0;
            return new ParsedIngredient(
                    f.name(), MockIngredientParser.quantityText(item),
                    (int) Math.round(f.kcalPer100g() * factor),
                    MockIngredientParser.round(f.proteinPer100g() * factor),
                    MockIngredientParser.round(f.carbsPer100g() * factor),
                    MockIngredientParser.round(f.fatPer100g() * factor),
                    MockIngredientParser.round(f.fiberPer100g() * factor));
        }).toList();
    }

    @Override
    public ParsedRecipe parseRecipe(String text) {
        if (text == null || text.isBlank()) return new ParsedRecipe("My recipe", 1, List.of());
        List<ParsedRecipeIngredient> lines = MealText.split(text).stream().map(item -> {
            Optional<FoodLibrary.Food> food = library.find(item.name());
            if (food.isEmpty()) return MockIngredientParser.estimateRecipeLine(item);
            FoodLibrary.Food f = food.get();
            return new ParsedRecipeIngredient(f.name(), f.kcalPer100g(), f.proteinPer100g(),
                    f.carbsPer100g(), f.fatPer100g(), f.fiberPer100g(), gramsOf(item));
        }).toList();
        return new ParsedRecipe(MockIngredientParser.suggestedRecipeName(text), 1, lines);
    }

    private static double gramsOf(MealText.Item item) {
        return item.grams() != null ? item.grams() : item.count() * MockIngredientParser.portionGrams(item.name());
    }
}
