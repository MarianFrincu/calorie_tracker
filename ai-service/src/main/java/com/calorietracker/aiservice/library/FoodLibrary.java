package com.calorietracker.aiservice.library;

import java.util.Optional;

/** The app's food library (public foods + the caller's own), as seen by the parser. */
public interface FoodLibrary {

    /** Nutrition per 100 g of a library food. */
    record Food(String name, int kcalPer100g, double proteinPer100g, double carbsPer100g,
                double fatPer100g, double fiberPer100g) {}

    /**
     * Best library match for a food name the user typed, e.g. "eggs" -> "Egg".
     * Empty when nothing sensible matches or the library can't be reached.
     */
    Optional<Food> find(String name);
}
