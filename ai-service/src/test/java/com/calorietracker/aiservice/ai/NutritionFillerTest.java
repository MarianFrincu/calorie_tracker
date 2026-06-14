package com.calorietracker.aiservice.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks in the "fiber=0, derive missing macros from kcal" behavior the
 *  normalizer adds on top of any IngredientParser implementation. */
class NutritionFillerTest {

    @Test
    void fillsFiberToZeroWhenMissing() {
        var item = new ParsedIngredient("steak", "200g", 400, 50, 0, 25, -1);
        var out = NutritionFiller.fillItems(List.of(item)).get(0);
        assertThat(out.fiber()).isZero();
    }

    @Test
    void keepsAllMacrosWhenAllProvided() {
        var item = new ParsedIngredient("apple", "150g", 80, 0.3, 21, 0.3, 3);
        var out = NutritionFiller.fillItems(List.of(item)).get(0);
        assertThat(out.protein()).isEqualTo(0.3);
        assertThat(out.carbs()).isEqualTo(21);
        assertThat(out.fat()).isEqualTo(0.3);
        assertThat(out.fiber()).isEqualTo(3);
    }

    @Test
    void fillsAllMissingMacrosFromKcal() {
        // 200 kcal split 40/40/20 -> 80 kcal P, 80 kcal C, 40 kcal F
        // -> 20 g P, 20 g C, ~4.4 g F. Approx is fine.
        var item = new ParsedIngredient("mystery dish", "1", 200, 0, 0, 0, 0);
        var out = NutritionFiller.fillItems(List.of(item)).get(0);
        double reconstructed = 4 * out.protein() + 4 * out.carbs() + 9 * out.fat();
        assertThat(reconstructed).isCloseTo(200, org.assertj.core.data.Offset.offset(1.5));
    }

    @Test
    void fillsOnlyTheMissingMacroAndKeepsRest() {
        // kcal=300; protein=20g (80 kcal), carbs=40g (160 kcal); fat missing.
        // Remaining = 300 - 80 - 160 = 60 kcal -> all to fat -> ~6.7 g.
        var item = new ParsedIngredient("bowl", "1", 300, 20, 40, 0, 5);
        var out = NutritionFiller.fillItems(List.of(item)).get(0);
        assertThat(out.protein()).isEqualTo(20);
        assertThat(out.carbs()).isEqualTo(40);
        assertThat(out.fat()).isCloseTo(6.7, org.assertj.core.data.Offset.offset(0.2));
        assertThat(out.fiber()).isEqualTo(5);
    }

    @Test
    void zeroKcalProducesZeroMacrosForNullEntry() {
        var item = new ParsedIngredient("water", "200ml", 0, 0, 0, 0, 0);
        var out = NutritionFiller.fillItems(List.of(item)).get(0);
        assertThat(out.protein()).isZero();
        assertThat(out.carbs()).isZero();
        assertThat(out.fat()).isZero();
        assertThat(out.fiber()).isZero();
    }

    @Test
    void recipeFillKeepsAmountGramsAndKcalIntact() {
        // Recipe variant uses the same balancer on per-100g numbers.
        var r = new ParsedRecipe("test", 1, List.of(
                new ParsedRecipeIngredient("unknown", 200, 0, 0, 0, -1, 75)));
        var out = NutritionFiller.fillRecipe(r);
        assertThat(out.ingredients()).hasSize(1);
        var ing = out.ingredients().get(0);
        assertThat(ing.amountGrams()).isEqualTo(75);
        assertThat(ing.kcalPer100g()).isEqualTo(200);
        assertThat(ing.fiberPer100g()).isZero();
        double reconstructed = 4 * ing.proteinPer100g() + 4 * ing.carbsPer100g() + 9 * ing.fatPer100g();
        assertThat(reconstructed).isCloseTo(200, org.assertj.core.data.Offset.offset(1.5));
    }
}
