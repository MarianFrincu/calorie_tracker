package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.util.ArrayList;
import java.util.List;

/**
 * Completes parser output: fiber defaults to 0, and macros the model left out
 * are filled from the unexplained calories (4P + 4C + 9F, split 40/40/20).
 * A zero counts as missing only when the other macros can't explain the
 * calories - chicken breast really has 0 g carbs.
 */
public final class NutritionFiller {

    private NutritionFiller() {}

    /** Per-portion variant: scales for ParsedIngredient (kcal int, others grams). */
    public static List<ParsedIngredient> fillItems(List<ParsedIngredient> items) {
        if (items == null) return List.of();
        List<ParsedIngredient> out = new ArrayList<>(items.size());
        for (ParsedIngredient i : items) {
            double[] m = balance(i.calories(), i.protein(), i.carbs(), i.fat());
            double fiber = i.fiber() < 0 ? 0 : i.fiber();
            out.add(new ParsedIngredient(i.name(), i.quantity(),
                    i.calories(), m[0], m[1], m[2], fiber));
        }
        return out;
    }

    /** Per-100g variant: same logic, applied to ParsedRecipeIngredient values. */
    public static ParsedRecipe fillRecipe(ParsedRecipe r) {
        if (r == null) return new ParsedRecipe("My recipe", 1, List.of());
        if (r.ingredients() == null || r.ingredients().isEmpty()) return r;
        List<ParsedRecipeIngredient> out = new ArrayList<>(r.ingredients().size());
        for (ParsedRecipeIngredient i : r.ingredients()) {
            double[] m = balance(i.kcalPer100g(), i.proteinPer100g(), i.carbsPer100g(), i.fatPer100g());
            double fiber = i.fiberPer100g() < 0 ? 0 : i.fiberPer100g();
            out.add(new ParsedRecipeIngredient(i.name(),
                    i.kcalPer100g(), m[0], m[1], m[2], fiber, i.amountGrams()));
        }
        return new ParsedRecipe(r.name(), r.servings(), out);
    }

    /** Known macros explaining at least this share of kcal means the zeros are real. */
    private static final double EXPLAINED_SHARE = 0.85;

    /**
     * Returns {protein, carbs, fat} (grams) such that 4P + 4C + 9F ≈ kcal.
     * Macros already provided (>0) are kept. Zeros are filled from the leftover
     * kcal (40/40/20 P/C/F mix) only when the provided macros explain less than
     * {@value #EXPLAINED_SHARE} of the calories. If everything is given and the
     * sum doesn't match kcal, we trust the AI and leave it alone — clamping
     * would punish a 5% rounding error.
     */
    private static double[] balance(int kcal, double p, double c, double f) {
        if (kcal <= 0) {
            return new double[]{Math.max(0, p), Math.max(0, c), Math.max(0, f)};
        }
        boolean pMissing = p <= 0;
        boolean cMissing = c <= 0;
        boolean fMissing = f <= 0;
        if (!pMissing && !cMissing && !fMissing) {
            return new double[]{p, c, f};
        }
        double kcalKnown = (pMissing ? 0 : 4 * p) + (cMissing ? 0 : 4 * c) + (fMissing ? 0 : 9 * f);
        if (kcalKnown >= EXPLAINED_SHARE * kcal) {
            return new double[]{Math.max(0, p), Math.max(0, c), Math.max(0, f)};
        }
        double remaining = Math.max(0, kcal - kcalKnown);

        // Weights describing how to split `remaining` across the missing macros.
        // 40% protein, 40% carbs, 20% fat — typical balanced diet.
        double wP = pMissing ? 0.40 : 0;
        double wC = cMissing ? 0.40 : 0;
        double wF = fMissing ? 0.20 : 0;
        double wSum = wP + wC + wF;
        if (wSum <= 0) return new double[]{p, c, f};

        double pOut = pMissing ? (remaining * (wP / wSum)) / 4.0 : p;
        double cOut = cMissing ? (remaining * (wC / wSum)) / 4.0 : c;
        double fOut = fMissing ? (remaining * (wF / wSum)) / 9.0 : f;

        return new double[]{round1(pOut), round1(cOut), round1(fOut)};
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
