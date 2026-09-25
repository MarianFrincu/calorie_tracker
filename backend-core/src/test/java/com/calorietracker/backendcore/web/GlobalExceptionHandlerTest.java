package com.calorietracker.backendcore.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void fieldNamesReadLikeTheFormLabels() {
        assertThat(GlobalExceptionHandler.fieldLabel("weightKg")).isEqualTo("Weight (kg)");
        assertThat(GlobalExceptionHandler.fieldLabel("heightCm")).isEqualTo("Height (cm)");
        assertThat(GlobalExceptionHandler.fieldLabel("dailyWaterTargetMl")).isEqualTo("Daily water target (ml)");
        assertThat(GlobalExceptionHandler.fieldLabel("ingredients[0].amountGrams")).isEqualTo("Amount (g)");
        assertThat(GlobalExceptionHandler.fieldLabel("kcalPer100g")).isEqualTo("Calories per 100 g");
        assertThat(GlobalExceptionHandler.fieldLabel("age")).isEqualTo("Age");
    }
}
