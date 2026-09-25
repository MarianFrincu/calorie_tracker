package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Diary entries (by food, by recipe, free-form), move / copy / delete, water and weight. */
class DiaryIntegrationTest extends IntegrationTest {

    private static String entry(String date, String meal, String rest) {
        return "{\"date\":\"" + date + "\",\"meal\":\"" + meal + "\"," + rest + "}";
    }

    @Test
    void addingAFoodScalesItsPer100gValues() throws Exception {
        String t = newUser();
        JsonNode e = json(post("/api/diary", t, entry(TODAY, "BREAKFAST", "\"ingredientId\":1,\"amountGrams\":150")), 201);
        assertThat(e.get("name").asString()).isEqualTo("Egg");
        assertThat(e.get("amount").asString()).isEqualTo("150 g");
        assertThat(e.get("kcal").asInt()).isEqualTo(233);          // 155 * 1.5 = 232.5
        assertThat(e.get("protein").asDouble()).isEqualTo(19.5);
        assertThat(e.get("fat").asDouble()).isEqualTo(16.5);
        assertThat(e.get("ingredientId").asLong()).isEqualTo(PUBLIC_EGG_ID);
    }

    @Test
    void addingARecipeUsesItsCookedWeightOrServings() throws Exception {
        String t = newUser();
        long oats = id(post("/api/ingredients", t,
                "{\"name\":\"Oats\",\"kcalPer100g\":380,\"proteinPer100g\":13,\"carbsPer100g\":67,\"fatPer100g\":7,\"fiberPer100g\":10}"));
        // 100 g egg (155) + 50 g oats (190) = 345 kcal, cooked weight 200 g.
        long recipe = id(post("/api/recipes", t, "{\"name\":\"Oat eggs\",\"ingredients\":[{\"ingredientId\":1,\"amountGrams\":100},"
                + "{\"ingredientId\":" + oats + ",\"amountGrams\":50}],\"totalCookedGrams\":200}"));

        JsonNode byGrams = json(post("/api/diary", t, entry(TODAY, "LUNCH", "\"recipeId\":" + recipe + ",\"amountGrams\":100")), 201);
        assertThat(byGrams.get("kcal").asInt()).isEqualTo(173);  // half the dish
        assertThat(byGrams.get("amount").asString()).isEqualTo("100.0 g");
        assertThat(byGrams.get("recipeId").asLong()).isEqualTo(recipe);

        JsonNode byServings = json(post("/api/diary", t, entry(TODAY, "DINNER", "\"recipeId\":" + recipe + ",\"servings\":2")), 201);
        assertThat(byServings.get("kcal").asInt()).isEqualTo(690);
        assertThat(byServings.get("amount").asString()).isEqualTo("2.0 servings");

        // A public recipe is usable by anyone.
        assertThat(post("/api/diary", t, entry(TODAY, "SNACK", "\"recipeId\":1,\"servings\":1")).statusCode()).isEqualTo(201);
    }

    @Test
    void freeFormEntriesFromTheAiFlow() throws Exception {
        String t = newUser();
        JsonNode e = json(post("/api/diary", t, entry(TODAY, "SNACK",
                "\"customName\":\"Apple\",\"customKcal\":95,\"customProtein\":0.5,\"customCarbs\":25,\"customFat\":0.3,\"customFiber\":4.4")), 201);
        assertThat(e.get("name").asString()).isEqualTo("Apple");
        assertThat(e.get("amount").isNull()).isTrue();
        assertThat(e.get("fiber").asDouble()).isEqualTo(4.4);
    }

    @Test
    void anEntryNeedsAFoodARecipeOrAName() throws Exception {
        String t = newUser();
        assertThat(post("/api/diary", t, entry(TODAY, "LUNCH", "\"customKcal\":100")).statusCode()).isEqualTo(400);
        assertThat(post("/api/diary", t, entry(TODAY, "LUNCH", "\"ingredientId\":1")).statusCode()).isEqualTo(400);
        assertThat(post("/api/diary", t, entry(TODAY, "LUNCH", "\"ingredientId\":99999999,\"amountGrams\":10")).statusCode())
                .isEqualTo(404);
        assertThat(post("/api/diary", t, entry(TODAY, "LUNCH", "\"recipeId\":99999999,\"servings\":1")).statusCode())
                .isEqualTo(404);
    }

    @Test
    void listsADaysEntriesByMealOrder() throws Exception {
        String t = newUser();
        post("/api/diary", t, entry(TODAY, "DINNER", "\"customName\":\"Pasta\",\"customKcal\":600"));
        post("/api/diary", t, entry(TODAY, "BREAKFAST", "\"customName\":\"Toast\",\"customKcal\":200"));
        post("/api/diary", t, entry(YESTERDAY, "LUNCH", "\"customName\":\"Old soup\",\"customKcal\":300"));
        List<JsonNode> day = diaryEntries(TODAY, t);
        assertThat(day).hasSize(2);
        assertThat(day.get(0).get("name").asString()).isEqualTo("Toast");
        assertThat(day.get(1).get("name").asString()).isEqualTo("Pasta");
    }

    @Test
    void moveCopyAndDelete() throws Exception {
        String t = newUser();
        long id = id(post("/api/diary", t, entry(TODAY, "LUNCH", "\"customName\":\"Salad\",\"customKcal\":150")));

        JsonNode copy = json(post("/api/diary/" + id + "/copy", t, "{\"date\":\"" + YESTERDAY + "\",\"meal\":\"DINNER\"}"), 201);
        assertThat(copy.get("id").asLong()).isNotEqualTo(id);
        assertThat(copy.get("date").asString()).isEqualTo(YESTERDAY);
        assertThat(copy.get("kcal").asInt()).isEqualTo(150);

        JsonNode moved = json(post("/api/diary/" + id + "/move", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"SNACK\"}"), 200);
        assertThat(moved.get("id").asLong()).isEqualTo(id);
        assertThat(moved.get("meal").asString()).isEqualTo("SNACK");

        assertThat(delete("/api/diary/" + id, t).statusCode()).isEqualTo(204);
        assertThat(delete("/api/diary/" + id, t).statusCode()).isEqualTo(404);
        assertThat(diaryEntries(YESTERDAY, t)).hasSize(1); // the copy survives
    }

    @Test
    void waterEntriesAddListAndDelete() throws Exception {
        String t = newUser();
        long a = id(post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":250}"));
        post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":500}");
        JsonNode water = json(get("/api/summary?date=" + TODAY, t), 200).get("water");
        assertThat(water.get("entries").size()).isEqualTo(2);
        assertThat(water.get("totalMl").asInt()).isEqualTo(750);
        assertThat(delete("/api/water/" + a, t).statusCode()).isEqualTo(204);
        assertThat(json(get("/api/summary?date=" + TODAY, t), 200).get("water").get("totalMl").asInt()).isEqualTo(500);
    }

    @Test
    void weightIsOneEntryPerDayAndListsByRange() throws Exception {
        String t = newUser();
        long first = id(post("/api/weight", t, "{\"date\":\"" + TODAY + "\",\"weightKg\":80.4}"));
        JsonNode again = json(post("/api/weight", t, "{\"date\":\"" + TODAY + "\",\"weightKg\":79.9}"), 201);
        assertThat(again.get("id").asLong()).isEqualTo(first);          // upsert, not a second row
        assertThat(again.get("weightKg").asDouble()).isEqualTo(79.9);
        post("/api/weight", t, "{\"date\":\"" + YESTERDAY + "\",\"weightKg\":80.6}");

        JsonNode all = json(get("/api/weight", t), 200);
        assertThat(all.size()).isEqualTo(2);
        assertThat(all.get(0).get("date").asString()).isEqualTo(YESTERDAY); // oldest first
        assertThat(json(get("/api/weight?from=" + TODAY + "&to=" + TODAY, t), 200).size()).isEqualTo(1);

        assertThat(delete("/api/weight/" + first, t).statusCode()).isEqualTo(204);
        assertThat(json(get("/api/weight", t), 200).size()).isEqualTo(1);
    }
}
