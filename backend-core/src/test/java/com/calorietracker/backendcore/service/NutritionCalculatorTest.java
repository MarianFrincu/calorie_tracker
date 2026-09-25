package com.calorietracker.backendcore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.calorietracker.backendcore.model.ActivityLevel;
import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import com.calorietracker.backendcore.model.Sex;
import org.junit.jupiter.api.Test;

/**
 * Math-only unit tests against the published Mifflin-St Jeor formula + the
 * macro percentages baked into {@link MacroPreset}. Catches the kind of bug
 * where someone "improves" the formula and BMR silently drifts.
 */
class NutritionCalculatorTest {

    @Test
    void bmr_male_25y_180cm_80kg_matches_published_formula() {
        // Mifflin-St Jeor: 10*80 + 6.25*180 - 5*25 + 5 = 800 + 1125 - 125 + 5 = 1805
        Integer bmr = NutritionCalculator.bmr(Sex.MALE, 25, 180.0, 80.0);
        assertThat(bmr).isEqualTo(1805);
    }

    @Test
    void bmr_female_30y_165cm_60kg_matches_published_formula() {
        // 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25 -> 1320
        Integer bmr = NutritionCalculator.bmr(Sex.FEMALE, 30, 165.0, 60.0);
        assertThat(bmr).isEqualTo(1320);
    }

    @Test
    void bmr_is_null_when_any_input_missing() {
        assertThat(NutritionCalculator.bmr(null, 25, 180.0, 80.0)).isNull();
        assertThat(NutritionCalculator.bmr(Sex.MALE, null, 180.0, 80.0)).isNull();
        assertThat(NutritionCalculator.bmr(Sex.MALE, 25, null, 80.0)).isNull();
        assertThat(NutritionCalculator.bmr(Sex.MALE, 25, 180.0, null)).isNull();
    }

    @Test
    void tdee_applies_activity_multiplier() {
        // BMR=1805, MODERATE=1.55 -> 2797.75 -> 2798
        Integer tdee = NutritionCalculator.tdeeMaintain(Sex.MALE, 25, 180.0, 80.0, ActivityLevel.MODERATE);
        assertThat(tdee).isEqualTo(2798);
    }

    @Test
    void macro_grams_balanced_preset_sums_to_calorie_target_within_rounding() {
        // BALANCED preset: 30% P, 50% C, 20% F.  protein/carbs at 4 kcal/g, fat at 9 kcal/g.
        NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(2000, MacroPreset.BALANCED, null);
        double kcal = g.protein() * 4 + g.carbs() * 4 + g.fat() * 9;
        // Rounding loses a few kcal at worst; within 5 kcal is fine.
        assertThat(kcal).isCloseTo(2000.0, within(5.0));
    }

    @Test
    void macro_grams_ketogenic_is_fat_dominant() {
        NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(2000, MacroPreset.KETOGENIC, null);
        // Ketogenic = ~70% fat; in grams that's ~155 g (2000 * 0.7 / 9).
        assertThat(g.fat()).isGreaterThan(g.protein());
        assertThat(g.fat()).isGreaterThan(g.carbs());
    }

    @Test
    void calorie_target_lose_20_percent() {
        // tdee=2798, LOSE 20% -> 2798 * 0.8 = 2238.4 -> 2238
        com.calorietracker.backendcore.model.AppUser u = new com.calorietracker.backendcore.model.AppUser();
        u.setSex(Sex.MALE);
        u.setAge(25);
        u.setHeightCm(180.0);
        u.setWeightKg(80.0);
        u.setActivityLevel(ActivityLevel.MODERATE);
        assertThat(NutritionCalculator.dailyCalorieTarget(u, Goal.LOSE, 20)).isEqualTo(2238);
    }

    @Test
    void calorie_target_clamps_percent_to_50() {
        com.calorietracker.backendcore.model.AppUser u = new com.calorietracker.backendcore.model.AppUser();
        u.setSex(Sex.MALE);
        u.setAge(25);
        u.setHeightCm(180.0);
        u.setWeightKg(80.0);
        u.setActivityLevel(ActivityLevel.MODERATE);
        // Asking for LOSE 999 -> clamped to 50 -> half of tdee.
        Integer clamped = NutritionCalculator.dailyCalorieTarget(u, Goal.LOSE, 999);
        Integer at50 = NutritionCalculator.dailyCalorieTarget(u, Goal.LOSE, 50);
        assertThat(clamped).isEqualTo(at50);
    }

    private static com.calorietracker.backendcore.model.AppUser male25y180cm80kg(ActivityLevel activity) {
        com.calorietracker.backendcore.model.AppUser u = new com.calorietracker.backendcore.model.AppUser();
        u.setSex(Sex.MALE);
        u.setAge(25);
        u.setHeightCm(180.0);
        u.setWeightKg(80.0);
        u.setActivityLevel(activity);
        return u;
    }

    @Test
    void a_losing_target_never_goes_below_bmr() {
        // BMR 1805. Sedentary TDEE 2166; LOSE 30% would be 1516 - raised to 1805.
        assertThat(NutritionCalculator.dailyCalorieTarget(male25y180cm80kg(ActivityLevel.SEDENTARY), Goal.LOSE, 30))
                .isEqualTo(1805);
        // Moderate TDEE 2798; LOSE 20% = 2238 is above BMR, untouched.
        assertThat(NutritionCalculator.dailyCalorieTarget(male25y180cm80kg(ActivityLevel.MODERATE), Goal.LOSE, 20))
                .isEqualTo(2238);
    }

    @Test
    void protein_is_capped_at_2_2_g_per_kg_and_the_rest_moves_to_carbs_and_fat() {
        // 60 kg -> at most 132 g. MAINTAIN_MUSCLE at 3000 kcal would be 262 g.
        NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(3000, MacroPreset.MAINTAIN_MUSCLE, 60.0);
        assertThat(g.protein()).isEqualTo(132);
        assertThat(g.carbs()).isEqualTo(380);
        assertThat(g.fat()).isEqualTo(106);
        assertThat(g.protein() * 4 + g.carbs() * 4 + g.fat() * 9).isCloseTo(3000, within(5));
    }

    @Test
    void capped_keto_protein_moves_mostly_to_fat() {
        NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(3000, MacroPreset.KETOGENIC, 60.0);
        assertThat(g.protein()).isEqualTo(132);
        assertThat(g.carbs()).isEqualTo(41);
        assertThat(g.fat()).isEqualTo(256);
    }

    @Test
    void protein_under_the_cap_is_untouched() {
        assertThat(NutritionCalculator.macroGrams(2000, MacroPreset.BALANCED, 80.0))
                .isEqualTo(NutritionCalculator.macroGrams(2000, MacroPreset.BALANCED, null));
    }
}
