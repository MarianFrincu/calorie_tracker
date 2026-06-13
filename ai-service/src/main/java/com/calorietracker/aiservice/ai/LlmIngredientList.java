package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import java.util.List;

/** Target type for Spring AI structured output. */
public record LlmIngredientList(List<ParsedIngredient> items) {
}
