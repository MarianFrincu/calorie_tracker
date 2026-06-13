package com.calorietracker.backendcore.dto;

import java.time.LocalDate;

public record DailyWaterPoint(LocalDate date, int ml, Integer mlTarget) {
}
