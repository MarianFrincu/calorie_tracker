package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.backendcore.support.TestJwts;
import org.junit.jupiter.api.Test;

/** Account deletion (DELETE /api/profile). */
class AccountIntegrationTest extends IntegrationTest {

    @Test
    void deletingTheAccountRemovesAllOfItsDataAndOnlyItsData() throws Exception {
        String sub = newSub();
        String t = TestJwts.forUser(sub);
        String other = userWithProfile();
        long othersFood = id(post("/api/ingredients", other,
                "{\"name\":\"Other food\",\"kcalPer100g\":100,\"proteinPer100g\":1,\"carbsPer100g\":1,\"fatPer100g\":1,\"fiberPer100g\":0}"));

        put("/api/profile", t, "{\"sex\":\"FEMALE\",\"age\":30,\"heightCm\":165,\"weightKg\":60,\"activityLevel\":\"LIGHT\"}");
        put("/api/objective", t, "{\"goal\":\"LOSE\",\"goalPercent\":10}");
        long food = id(post("/api/ingredients", t,
                "{\"name\":\"Oats\",\"kcalPer100g\":380,\"proteinPer100g\":13,\"carbsPer100g\":67,\"fatPer100g\":7,\"fiberPer100g\":10}"));
        post("/api/recipes", t, "{\"name\":\"Porridge\",\"ingredients\":[{\"ingredientId\":" + food + ",\"amountGrams\":80}]}");
        post("/api/recipes/from-ai", t, "{\"name\":\"AI bowl\",\"ingredients\":[{\"name\":\"Kale\",\"kcalPer100g\":49,"
                + "\"proteinPer100g\":4,\"carbsPer100g\":9,\"fatPer100g\":1,\"fiberPer100g\":4,\"amountGrams\":50}]}");
        post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"BREAKFAST\",\"ingredientId\":" + food + ",\"amountGrams\":80}");
        post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":500}");
        post("/api/weight", t, "{\"date\":\"" + TODAY + "\",\"weightKg\":60.5}");
        long userId = jdbc.queryForObject("select id from app_users where user_key = ?", Long.class, sub);

        assertThat(delete("/api/profile", t).statusCode()).isEqualTo(204);

        for (String table : new String[]{"diary_entries", "water_entries", "weight_log", "objective_history"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userId))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from ingredients where owner_user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from recipes where owner_user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_users where user_key = ?", Integer.class, sub)).isZero();

        // Nobody else is affected, and signing in again starts from a blank profile.
        assertThat(get("/api/ingredients/" + othersFood, other).statusCode()).isEqualTo(200);
        assertThat(get("/api/profile", t).body()).contains("\"sex\":null");
        assertThat(get("/api/ingredients/" + PUBLIC_EGG_ID, t).statusCode()).isEqualTo(200);
    }
}
