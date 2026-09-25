package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The food library (public + own), recipes, and recipes created from AI blueprints. */
class FoodsAndRecipesIntegrationTest extends IntegrationTest {

    private static String food(String name, int kcal) {
        return "{\"name\":\"" + name + "\",\"brand\":\"Home\",\"kcalPer100g\":" + kcal
                + ",\"proteinPer100g\":10,\"carbsPer100g\":20,\"fatPer100g\":5,\"fiberPer100g\":3}";
    }

    @Test
    void ownFoodsCrud() throws Exception {
        String t = newUser();
        JsonNode created = json(post("/api/ingredients", t, food("Granola", 450)), 201);
        long id = created.get("id").asLong();
        assertThat(created.get("isPublic").asBoolean()).isFalse();
        assertThat(created.get("brand").asString()).isEqualTo("Home");

        assertThat(json(get("/api/ingredients/" + id, t), 200).get("kcalPer100g").asInt()).isEqualTo(450);
        JsonNode updated = json(put("/api/ingredients/" + id, t, food("Granola, honey", 470)), 200);
        assertThat(updated.get("name").asString()).isEqualTo("Granola, honey");
        assertThat(updated.get("kcalPer100g").asInt()).isEqualTo(470);

        assertThat(delete("/api/ingredients/" + id, t).statusCode()).isEqualTo(204);
        assertThat(get("/api/ingredients/" + id, t).statusCode()).isEqualTo(404);
    }

    @Test
    void publicFoodsAndRecipesAreReadOnly() throws Exception {
        String t = newUser();
        assertThat(json(get("/api/ingredients/" + PUBLIC_EGG_ID, t), 200).get("isPublic").asBoolean()).isTrue();
        assertThat(put("/api/ingredients/" + PUBLIC_EGG_ID, t, food("Hacked egg", 1)).statusCode()).isEqualTo(409);
        assertThat(delete("/api/ingredients/" + PUBLIC_EGG_ID, t).statusCode()).isEqualTo(409);
        assertThat(delete("/api/recipes/1", t).statusCode()).isEqualTo(409);
        assertThat(put("/api/recipes/1", t,
                "{\"name\":\"x\",\"ingredients\":[{\"ingredientId\":1,\"amountGrams\":10}]}").statusCode()).isEqualTo(409);
    }

    @Test
    void aFoodUsedByARecipeCannotBeDeleted() throws Exception {
        String t = newUser();
        long f = id(post("/api/ingredients", t, food("Tahini", 595)));
        post("/api/recipes", t, "{\"name\":\"Hummus\",\"ingredients\":[{\"ingredientId\":" + f + ",\"amountGrams\":30}]}");
        JsonNode err = json(delete("/api/ingredients/" + f, t), 409);
        assertThat(err.get("message").asString()).contains("used in 1 recipe");
    }

    @Test
    void searchScopesTyposAndPaging() throws Exception {
        String t = newUser();
        post("/api/ingredients", t, food("Egg salad, homemade", 200));

        JsonNode mine = json(get("/api/ingredients?q=egg", t), 200);
        assertThat(mine.size()).isEqualTo(1);                     // scope=mine: only own foods
        JsonNode all = json(get("/api/ingredients?scope=all&q=egg", t), 200);
        assertThat(all.size()).isGreaterThan(1);
        assertThat(all.get(0).get("name").asString()).isEqualTo("Egg salad, homemade"); // own first

        JsonNode typo = json(get("/api/ingredients?scope=all&q=zucini", t), 200);
        assertThat(typo.get(0).get("name").asString()).isEqualTo("Zucchini");

        assertThat(json(get("/api/ingredients?scope=all&size=1000", t), 200).size()).isLessThanOrEqualTo(100);
        JsonNode page0 = json(get("/api/ingredients?scope=all&size=5&page=0", t), 200);
        JsonNode page1 = json(get("/api/ingredients?scope=all&size=5&page=1", t), 200);
        assertThat(page0.size()).isEqualTo(5);
        assertThat(page0.get(0).get("id")).isNotEqualTo(page1.get(0).get("id"));
        assertThat(get("/api/ingredients?scope=all&page=-3", t).statusCode()).isEqualTo(200);
        assertThat(json(get("/api/ingredients?scope=all&q=%27%20OR%201%3D1--", t), 200).size()).isZero();
    }

    @Test
    void recipesComputeTotalsAndCanBeRebuilt() throws Exception {
        String t = newUser();
        JsonNode r = json(post("/api/recipes", t, "{\"name\":\"Egg bowl\",\"ingredients\":["
                + "{\"ingredientId\":1,\"amountGrams\":200}],\"totalCookedGrams\":180}"), 201);
        long id = r.get("id").asLong();
        assertThat(r.get("totalKcal").asInt()).isEqualTo(310);
        assertThat(r.get("totalProtein").asDouble()).isEqualTo(26.0);
        assertThat(r.get("totalCookedGrams").asDouble()).isEqualTo(180.0);
        assertThat(r.get("ingredients").get(0).get("ingredientName").asString()).isEqualTo("Egg");

        // No cooked weight given: falls back to the raw sum.
        JsonNode raw = json(post("/api/recipes", t, "{\"name\":\"Plain egg\",\"ingredients\":["
                + "{\"ingredientId\":1,\"amountGrams\":120}]}"), 201);
        assertThat(raw.get("totalCookedGrams").asDouble()).isEqualTo(120.0);

        JsonNode updated = json(put("/api/recipes/" + id, t, "{\"name\":\"Big egg bowl\",\"ingredients\":["
                + "{\"ingredientId\":1,\"amountGrams\":300}],\"totalCookedGrams\":260}"), 200);
        assertThat(updated.get("name").asString()).isEqualTo("Big egg bowl");
        assertThat(updated.get("totalKcal").asInt()).isEqualTo(465);
        assertThat(updated.get("ingredients").size()).isEqualTo(1);

        assertThat(json(get("/api/recipes?q=bowl", t), 200).size()).isEqualTo(1);
        assertThat(json(get("/api/recipes?scope=all&q=avocado", t), 200).size()).isGreaterThanOrEqualTo(1);
        assertThat(delete("/api/recipes/" + id, t).statusCode()).isEqualTo(204);
        assertThat(get("/api/recipes/" + id, t).statusCode()).isEqualTo(404);
    }

    @Test
    void deletingARecipeKeepsDiaryHistory() throws Exception {
        String t = newUser();
        long recipe = id(post("/api/recipes", t, "{\"name\":\"Omelette\",\"ingredients\":[{\"ingredientId\":1,\"amountGrams\":100}]}"));
        post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"BREAKFAST\",\"recipeId\":" + recipe + ",\"servings\":1}");
        assertThat(delete("/api/recipes/" + recipe, t).statusCode()).isEqualTo(204);
        List<JsonNode> day = diaryEntries(TODAY, t);
        assertThat(day.get(0).get("name").asString()).isEqualTo("Omelette");
        assertThat(day.get(0).get("kcal").asInt()).isEqualTo(155);
        assertThat(day.get(0).get("recipeId").isNull()).isTrue();
    }

    @Test
    void aiBlueprintsBecomeARecipeWithPrivateFoods() throws Exception {
        String t = newUser();
        JsonNode r = json(post("/api/recipes/from-ai", t, "{\"name\":\"Chicken rice\",\"servings\":1,\"totalCookedGrams\":400,"
                + "\"ingredients\":[{\"name\":\"Chicken\",\"kcalPer100g\":165,\"proteinPer100g\":31,\"carbsPer100g\":0,"
                + "\"fatPer100g\":3.6,\"fiberPer100g\":0,\"amountGrams\":200},{\"name\":\"Rice\",\"kcalPer100g\":130,"
                + "\"proteinPer100g\":2.7,\"carbsPer100g\":28,\"fatPer100g\":0.3,\"fiberPer100g\":0.4,\"amountGrams\":150}]}"), 201);
        assertThat(r.get("totalKcal").asInt()).isEqualTo(525);
        assertThat(r.get("totalCookedGrams").asDouble()).isEqualTo(400.0);
        assertThat(r.get("isPublic").asBoolean()).isFalse();
        assertThat(json(get("/api/ingredients?q=chicken", t), 200).size()).isEqualTo(1); // now in "my foods"

        assertThat(post("/api/recipes/from-ai", t, "{\"name\":\"Empty\",\"ingredients\":[]}").statusCode()).isEqualTo(400);
        assertThat(post("/api/recipes/from-ai", t, "{\"name\":\"Huge\",\"ingredients\":[{\"name\":\"x\",\"kcalPer100g\":100,"
                + "\"proteinPer100g\":1,\"carbsPer100g\":1,\"fatPer100g\":1,\"fiberPer100g\":0,\"amountGrams\":1000000}]}")
                .statusCode()).isEqualTo(400);
    }
}
