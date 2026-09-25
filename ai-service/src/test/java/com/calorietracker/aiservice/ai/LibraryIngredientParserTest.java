package com.calorietracker.aiservice.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.library.FoodLibrary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LibraryIngredientParserTest {

    /** A tiny in-memory library: exact names only, like a perfect search. */
    private static final Map<String, FoodLibrary.Food> FOODS = Map.of(
            "egg", new FoodLibrary.Food("Egg", 155, 13, 1.1, 11, 0),
            "rice", new FoodLibrary.Food("White rice, cooked", 130, 2.7, 28, 0.3, 0.4),
            "banana", new FoodLibrary.Food("Banana", 89, 1.1, 23, 0.3, 2.6));

    private final LibraryIngredientParser parser = new LibraryIngredientParser(
            name -> Optional.ofNullable(FOODS.get(MealText.singular(name))));

    @Test
    void usesLibraryValuesScaledByCountTimesTypicalPortion() {
        // 2 eggs = 2 x 50 g = 100 g -> exactly the per-100 g values.
        ParsedIngredient eggs = parser.parse("2 eggs").get(0);
        assertThat(eggs.name()).isEqualTo("Egg");
        assertThat(eggs.quantity()).isEqualTo("2");
        assertThat(eggs.calories()).isEqualTo(155);
        assertThat(eggs.protein()).isEqualTo(13.0);
    }

    @Test
    void usesExplicitWeights() {
        ParsedIngredient rice = parser.parse("rice 150g").get(0);
        assertThat(rice.name()).isEqualTo("White rice, cooked");
        assertThat(rice.quantity()).isEqualTo("150 g");
        assertThat(rice.calories()).isEqualTo(195);
        assertThat(rice.carbs()).isEqualTo(42.0);
    }

    @Test
    void fallsBackToTheBuiltInTableForUnknownFoods() {
        List<ParsedIngredient> items = parser.parse("banana, space dust");
        assertThat(items).extracting(ParsedIngredient::name).containsExactly("Banana", "Space dust");
        assertThat(items.get(1).calories()).isEqualTo(100);
    }

    @Test
    void recipeLinesCarryPer100gValuesAndGramsUsed() {
        ParsedRecipe r = parser.parseRecipe("rice 200g and 3 eggs");
        assertThat(r.ingredients()).hasSize(2);
        assertThat(r.ingredients().get(0).kcalPer100g()).isEqualTo(130);
        assertThat(r.ingredients().get(0).amountGrams()).isEqualTo(200.0);
        assertThat(r.ingredients().get(1).name()).isEqualTo("Egg");
        assertThat(r.ingredients().get(1).amountGrams()).isEqualTo(150.0);
        assertThat(r.name()).isEqualTo("Rice 200g and 3 eggs");
    }

    @Test
    void emptyTextGivesNothing() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parseRecipe(" ").ingredients()).isEmpty();
    }
}
