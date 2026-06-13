package com.calorietracker.desktop.model;

/** Body for POST /api/diary/{id}/move and /api/diary/{id}/copy. */
public record MoveOrCopyDiaryRequest(String date, Meal meal) {
}
