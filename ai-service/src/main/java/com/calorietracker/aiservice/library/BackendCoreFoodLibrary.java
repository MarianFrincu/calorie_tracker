package com.calorietracker.aiservice.library;

import com.calorietracker.aiservice.ai.MealText;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * {@link FoodLibrary} backed by backend-core's own search endpoint
 * ({@code GET /api/ingredients?scope=all}), called with the caller's bearer
 * token (see BackendCoreConfig) so results include their private foods and
 * nothing else. That search
 * is typo-tolerant, so "zucini" still finds "Zucchini".
 *
 * <p>Among the candidates it prefers an exact name, then a name starting with
 * the query, then the shortest name - so "rice" picks "Rice, cooked" over
 * "Rice cakes, puffed".
 */
public class BackendCoreFoodLibrary implements FoodLibrary {

    private static final Logger log = LoggerFactory.getLogger(BackendCoreFoodLibrary.class);

    /** Wire shape of backend-core's IngredientResponse (unused fields ignored). */
    record Ingredient(String name, int kcalPer100g, double proteinPer100g, double carbsPer100g,
                      double fatPer100g, double fiberPer100g) {}

    private final RestClient client;

    public BackendCoreFoodLibrary(RestClient client) {
        this.client = client;
    }

    /**
     * What a bare generic word usually means. Without this "rice" would pick
     * "Rice noodles" and "milk" "Almond milk". Only used when the user has no
     * food with exactly that name, and only for the bare word ("brown rice"
     * still searches as typed). Names are from the public library seed.
     */
    private static final Map<String, String> EVERYDAY = Map.ofEntries(
            Map.entry("rice", "White rice, cooked"), Map.entry("chicken", "Chicken breast, cooked"),
            Map.entry("milk", "Whole milk"), Map.entry("cheese", "Cheddar cheese"),
            Map.entry("yogurt", "Plain yogurt"), Map.entry("bread", "White bread"),
            Map.entry("toast", "White bread"), Map.entry("potato", "Potato, boiled"),
            Map.entry("beef", "Beef, ground, cooked"), Map.entry("steak", "Beef steak, cooked"),
            Map.entry("oat", "Rolled oats, dry"), Map.entry("pasta", "Pasta, cooked"),
            Map.entry("coffee", "Coffee, black"), Map.entry("tea", "Tea, plain"),
            Map.entry("tuna", "Tuna, canned in water"), Map.entry("turkey", "Turkey breast, cooked"),
            Map.entry("pork", "Pork loin, cooked"), Map.entry("salmon", "Salmon, cooked"),
            Map.entry("bean", "Black beans, cooked"), Map.entry("juice", "Orange juice"));

    @Override
    public Optional<Food> find(String name) {
        String singular = MealText.singular(name);
        List<Ingredient> hits = search(singular);
        if (hits.isEmpty() && !singular.equals(name)) {
            hits = search(name);
        }
        // An exact name (e.g. the user's own "Rice") always wins.
        Optional<Ingredient> exact = hits.stream()
                .filter(i -> i.name().equalsIgnoreCase(singular) || i.name().equalsIgnoreCase(name)).findFirst();
        if (exact.isPresent()) return exact.map(BackendCoreFoodLibrary::toFood);

        String everyday = EVERYDAY.get(singular);
        if (everyday != null) {
            Optional<Ingredient> usual = search(everyday).stream()
                    .filter(i -> i.name().equalsIgnoreCase(everyday)).findFirst();
            if (usual.isPresent()) return usual.map(BackendCoreFoodLibrary::toFood);
        }
        return hits.isEmpty() ? Optional.empty() : Optional.of(toFood(best(singular, hits)));
    }

    private static Food toFood(Ingredient i) {
        return new Food(i.name(), i.kcalPer100g(), i.proteinPer100g(), i.carbsPer100g(), i.fatPer100g(), i.fiberPer100g());
    }

    private List<Ingredient> search(String query) {
        try {
            List<Ingredient> candidates = client.get()
                    .uri(u -> u.path("/api/ingredients")
                            .queryParam("scope", "all")
                            .queryParam("q", query)
                            .queryParam("size", 10)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Ingredient>>() {});
            return candidates == null ? List.of() : candidates;
        } catch (RuntimeException e) {
            // Library down or slow: the parser falls back to its built-in table.
            log.warn("Food library lookup for '{}' failed: {}", query, e.getMessage());
            return List.of();
        }
    }

    static Ingredient best(String query, List<Ingredient> candidates) {
        String q = query.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .min(Comparator.<Ingredient>comparingInt(i -> rank(q, i.name().toLowerCase(Locale.ROOT)))
                        .thenComparingInt(i -> i.name().length()))
                .orElseThrow();
    }

    private static int rank(String q, String name) {
        if (name.equals(q)) return 0;
        if (name.startsWith(q + ",") || name.startsWith(q + " ")) return 1;
        if (name.startsWith(q)) return 2;
        if (name.contains(q)) return 3;
        return 4; // fuzzy (typo) match from the backend
    }
}
