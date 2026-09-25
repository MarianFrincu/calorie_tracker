package com.calorietracker.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Same numbers as the backend's NutritionCalculatorTest and the web's nutrition.test.ts. */
class NutritionCalcTest {

    @Test
    void capsProteinAt2point2GramsPerKgLikeTheBackend() {
        assertEquals(new NutritionCalc.MacroGrams(176, 367, 65), NutritionCalc.macroGrams(2759, MacroPreset.BALANCED, 80.0));
        assertEquals(new NutritionCalc.MacroGrams(132, 380, 106), NutritionCalc.macroGrams(3000, MacroPreset.MAINTAIN_MUSCLE, 60.0));
        assertEquals(new NutritionCalc.MacroGrams(132, 41, 256), NutritionCalc.macroGrams(3000, MacroPreset.KETOGENIC, 60.0));
        assertEquals(NutritionCalc.macroGrams(2000, MacroPreset.BALANCED, null),
                NutritionCalc.macroGrams(2000, MacroPreset.BALANCED, 80.0));
        assertTrue(NutritionCalc.proteinCapped(2759, MacroPreset.BALANCED, 80.0));
        assertFalse(NutritionCalc.proteinCapped(2000, MacroPreset.BALANCED, 80.0));
    }

    @Test
    void aLosingTargetNeverGoesBelowBmr() {
        assertEquals(1805, NutritionCalc.target(2166, Goal.LOSE, 30, 1805));
        assertEquals(1949, NutritionCalc.target(2166, Goal.LOSE, 10, 1805));
        assertFalse(NutritionCalc.belowBmr(2166, 1805, 15));
        assertTrue(NutritionCalc.belowBmr(2166, 1805, 20));
    }
}
