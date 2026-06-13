package com.calorietracker.desktop.model;

/** Macro-distribution presets (protein/carbs/fat % of total calories). */
public enum MacroPreset {
    BALANCED(30, 50, 20),
    MAINTAIN_MUSCLE(35, 40, 25),
    KETOGENIC(25, 5, 70);

    public final int proteinPercent;
    public final int carbsPercent;
    public final int fatPercent;

    MacroPreset(int proteinPercent, int carbsPercent, int fatPercent) {
        this.proteinPercent = proteinPercent;
        this.carbsPercent = carbsPercent;
        this.fatPercent = fatPercent;
    }

    public String pretty() {
        return switch (this) {
            case BALANCED -> "Balanced (30 / 50 / 20)";
            case MAINTAIN_MUSCLE -> "Maintain muscle mass (35 / 40 / 25)";
            case KETOGENIC -> "Ketogenic (25 / 5 / 70)";
        };
    }
}
