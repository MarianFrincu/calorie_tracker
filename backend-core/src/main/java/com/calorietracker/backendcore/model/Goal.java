package com.calorietracker.backendcore.model;

/**
 * Direction the user wants their weight to move. The magnitude is now a
 * separate {@code goalPercent} field on AppUser / ObjectiveHistory (e.g. 20
 * means a 20% deficit when paired with {@link #LOSE}).
 */
public enum Goal {
    LOSE, MAINTAIN, GAIN
}
