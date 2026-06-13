package com.calorietracker.desktop.model;

/** One weight log entry. {@code date} is "yyyy-MM-dd". */
public record WeightEntry(Long id, String date, Double weightKg) {
}
