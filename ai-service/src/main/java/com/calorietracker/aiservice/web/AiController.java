package com.calorietracker.aiservice.web;

import com.calorietracker.aiservice.ai.IngredientParser;
import com.calorietracker.aiservice.dto.ParseRequest;
import com.calorietracker.aiservice.dto.ParseResult;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI parser endpoints. Routed by the gateway via lb://ai-service. Save-recipe
 * lives on backend-core (POST /api/recipes/from-ai) because only that service
 * owns the DB.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final IngredientParser parser;

    public AiController(IngredientParser parser) {
        this.parser = parser;
    }

    /** Diary-style parse: per-portion macros + quantity. */
    @PostMapping("/parse")
    public ParseResult parse(@Valid @RequestBody ParseRequest req) {
        return ParseResult.of(parser.parse(req.text()));
    }

    /** Recipe-style parse: per-100g macros + grams used. */
    @PostMapping("/parse-recipe")
    public ParsedRecipe parseRecipe(@Valid @RequestBody ParseRequest req) {
        return parser.parseRecipe(req.text());
    }
}
