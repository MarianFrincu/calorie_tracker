package com.calorietracker.backendcore.model;

/** TDEE multipliers applied to BMR (Mifflin-St Jeor). */
public enum ActivityLevel {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9);

    public final double multiplier;

    ActivityLevel(double multiplier) {
        this.multiplier = multiplier;
    }
}
