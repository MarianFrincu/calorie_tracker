package com.calorietracker.aiservice.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the offline (mock) parser. Spring context not required —
 * the parser is a plain Java class.
 */
class MockIngredientParserTest {

    private final MockIngredientParser parser = new MockIngredientParser();

    @Test
    void parse_splits_on_comma_and_and() {
        List<ParsedIngredient> items = parser.parse("2 eggs, toast and butter");
        assertThat(items).extracting(ParsedIngredient::name)
                .containsExactly("Egg", "Toast", "Butter");
    }

    @Test
    void parse_scales_macros_by_leading_quantity() {
        List<ParsedIngredient> items = parser.parse("3 eggs");
        // One egg is 72 kcal in the lookup table -> 3 eggs = 216 kcal.
        assertThat(items).hasSize(1);
        assertThat(items.get(0).calories()).isEqualTo(216);
        assertThat(items.get(0).quantity()).isEqualTo("3");
    }

    @Test
    void parse_empty_input_returns_empty_list() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void parse_unknown_item_falls_back_to_default_macros() {
        List<ParsedIngredient> items = parser.parse("space dust");
        assertThat(items).hasSize(1);
        // DEFAULT_MACROS = {100, 5, 15, 3, 2, 100}
        assertThat(items.get(0).calories()).isEqualTo(100);
    }

    @Test
    void parseRecipe_returns_per_100g_macros_and_total_grams() {
        ParsedRecipe r = parser.parseRecipe("2 eggs and toast");
        assertThat(r.ingredients()).hasSize(2);
        // Egg: 72 kcal / 50 g portion = 144 kcal per 100 g.
        assertThat(r.ingredients().get(0).kcalPer100g()).isEqualTo(144);
        // 2 portions * 50 g = 100 g used.
        assertThat(r.ingredients().get(0).amountGrams()).isEqualTo(100.0);
    }

    @Test
    void parseRecipe_handles_empty_input() {
        ParsedRecipe r = parser.parseRecipe("");
        assertThat(r.name()).isEqualTo("My recipe");
        assertThat(r.ingredients()).isEmpty();
    }
}
