package com.calorietracker.backendcore.model;

/**
 * Macro-distribution presets. The percentages add up to 100 and represent
 * the share of total daily calories from each macronutrient.
 */
public enum MacroPreset {
    /** Standard healthy split. */
    BALANCED(30, 50, 20),
    /** Higher protein, moderate carbs, lower fat. Good for retaining muscle. */
    MAINTAIN_MUSCLE(35, 40, 25),
    /** Very low carb, very high fat. */
    KETOGENIC(25, 5, 70);

    public final int proteinPercent;
    public final int carbsPercent;
    public final int fatPercent;

    MacroPreset(int proteinPercent, int carbsPercent, int fatPercent) {
        this.proteinPercent = proteinPercent;
        this.carbsPercent = carbsPercent;
        this.fatPercent = fatPercent;
    }
}
