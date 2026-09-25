package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.backendcore.support.TestJwts;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Authentication, user provisioning, and the error contract (4xx, never 500, for client mistakes). */
class SecurityAndErrorsIntegrationTest extends IntegrationTest {

    @Test
    void rejectsMissingExpiredIdAndOtherClientTokens() throws Exception {
        assertThat(get("/api/profile", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/profile", TestJwts.expired(newSub())).statusCode()).isEqualTo(401);
        assertThat(get("/api/profile", TestJwts.idToken(newSub())).statusCode()).isEqualTo(401);
        assertThat(get("/api/profile", TestJwts.forOtherClient(newSub())).statusCode()).isEqualTo(401);
        assertThat(get("/api/profile", "not-a-jwt").statusCode()).isEqualTo(401);
    }

    @Test
    void healthIsPublicAndTerse() throws Exception {
        HttpResponse<String> r = get("/actuator/health", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("UP").doesNotContain("db").doesNotContain("diskSpace");
    }

    @Test
    void firstRequestFromNewUserMayBeAnyReadEndpoint() throws Exception {
        for (String path : List.of("/api/summary", "/api/ingredients?scope=all&q=egg",
                "/api/recipes", "/api/reports/nutrition", "/api/reports/water", "/api/weight",
                "/api/objective", "/api/profile")) {
            assertThat(get(path, newUser()).statusCode()).as(path).isEqualTo(200);
        }
    }

    @Test
    void concurrentFirstRequestsCreateExactlyOneUser() {
        String sub = newSub();
        String token = TestJwts.forUser(sub);
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String path = i % 2 == 0 ? "/api/profile" : "/api/summary";
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return get(path, token);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        calls.forEach(c -> assertThat(c.join().statusCode()).isEqualTo(200));
        assertThat(jdbc.queryForObject("select count(*) from app_users where user_key = ?", Integer.class, sub))
                .isEqualTo(1);
    }

    @Test
    void clientMistakesMapToTheRight4xxWithAReadableMessage() throws Exception {
        String t = newUser();
        assertThat(json(post("/api/water", t, "{\"date\":"), 400).get("message").asString())
                .contains("not valid JSON");
        assertThat(json(post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"BRUNCH\",\"customName\":\"x\"}"), 400)
                .get("message").asString()).contains("meal");
        assertThat(delete("/api/diary/abc", t).statusCode()).isEqualTo(400);
        assertThat(get("/api/summary?date=yesterday", t).statusCode()).isEqualTo(400);
        assertThat(get("/api/nope", t).statusCode()).isEqualTo(404);
        HttpResponse<String> wrongMethod = send("PATCH", "/api/profile", t, "{}");
        assertThat(wrongMethod.statusCode()).isEqualTo(405);
        assertThat(wrongMethod.body()).doesNotContain("Exception");
        assertThat(send("POST", "/api/water", t, "not json at all").statusCode()).isEqualTo(400);
    }

    @Test
    void errorBodiesHaveTheStandardShape() throws Exception {
        var body = json(delete("/api/diary/999999999", newUser()), 404);
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("error").asString()).isEqualTo("Not Found");
        assertThat(body.get("message").asString()).contains("not found");
    }

    @Test
    void enforcesUpperBoundsAndRequiredFields() throws Exception {
        String t = newUser();
        assertThat(post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":2000000000}").statusCode()).isEqualTo(400);
        assertThat(post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":0}").statusCode()).isEqualTo(400);
        assertThat(post("/api/water", t, "{\"date\":\"9999-12-31\",\"ml\":100}").statusCode()).isEqualTo(400);
        assertThat(post("/api/water", t, "{\"date\":\"1999-12-31\",\"ml\":100}").statusCode()).isEqualTo(400);
        assertThat(post("/api/water", t, "{\"ml\":100}").statusCode()).isEqualTo(400);
        assertThat(post("/api/weight", t, "{\"date\":\"" + TODAY + "\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/weight", t, "{\"date\":\"" + TODAY + "\",\"weightKg\":5}").statusCode()).isEqualTo(400);
        assertThat(post("/api/recipes", t, "{\"name\":\"x\",\"ingredients\":[{\"ingredientId\":1}]}").statusCode())
                .isEqualTo(400);
        assertThat(post("/api/recipes", t, "{\"name\":\"x\",\"ingredients\":[]}").statusCode()).isEqualTo(400);
        assertThat(post("/api/ingredients", t,
                "{\"name\":\"\",\"kcalPer100g\":1,\"proteinPer100g\":0,\"carbsPer100g\":0,\"fatPer100g\":0,\"fiberPer100g\":0}")
                .statusCode()).isEqualTo(400);
        assertThat(post("/api/ingredients", t,
                "{\"name\":\"x\",\"kcalPer100g\":5000,\"proteinPer100g\":0,\"carbsPer100g\":0,\"fatPer100g\":0,\"fiberPer100g\":0}")
                .statusCode()).isEqualTo(400);
        assertThat(put("/api/objective", t, "{\"dailyWaterTargetMl\":10000000}").statusCode()).isEqualTo(400);
        assertThat(put("/api/objective", t, "{\"goalPercent\":80}").statusCode()).isEqualTo(400);
        assertThat(put("/api/profile", t, "{\"age\":200}").statusCode()).isEqualTo(400);
        assertThat(post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":250}").statusCode()).isEqualTo(201);
    }

    @Test
    void usersCannotSeeOrChangeEachOthersData() throws Exception {
        String alice = newUser();
        String bob = newUser();
        long entry = id(post("/api/diary", alice,
                "{\"date\":\"" + TODAY + "\",\"meal\":\"LUNCH\",\"customName\":\"soup\",\"customKcal\":200}"));
        long food = id(post("/api/ingredients", alice,
                "{\"name\":\"Secret sauce\",\"kcalPer100g\":100,\"proteinPer100g\":1,\"carbsPer100g\":1,\"fatPer100g\":1,\"fiberPer100g\":0}"));
        long recipe = id(post("/api/recipes", alice,
                "{\"name\":\"Secret stew\",\"ingredients\":[{\"ingredientId\":" + food + ",\"amountGrams\":100}]}"));
        long water = id(post("/api/water", alice, "{\"date\":\"" + TODAY + "\",\"ml\":300}"));
        long weight = id(post("/api/weight", alice, "{\"date\":\"" + TODAY + "\",\"weightKg\":70}"));

        assertThat(delete("/api/diary/" + entry, bob).statusCode()).isEqualTo(404);
        assertThat(post("/api/diary/" + entry + "/move", bob, "{\"date\":\"" + TODAY + "\",\"meal\":\"DINNER\"}").statusCode())
                .isEqualTo(404);
        assertThat(post("/api/diary/" + entry + "/copy", bob, "{\"date\":\"" + TODAY + "\",\"meal\":\"DINNER\"}").statusCode())
                .isEqualTo(404);
        assertThat(get("/api/ingredients/" + food, bob).statusCode()).isEqualTo(404);
        assertThat(put("/api/ingredients/" + food, bob,
                "{\"name\":\"x\",\"kcalPer100g\":1,\"proteinPer100g\":0,\"carbsPer100g\":0,\"fatPer100g\":0,\"fiberPer100g\":0}")
                .statusCode()).isEqualTo(404);
        assertThat(delete("/api/ingredients/" + food, bob).statusCode()).isEqualTo(404);
        assertThat(get("/api/recipes/" + recipe, bob).statusCode()).isEqualTo(404);
        assertThat(delete("/api/recipes/" + recipe, bob).statusCode()).isEqualTo(404);
        assertThat(post("/api/diary", bob, "{\"date\":\"" + TODAY + "\",\"meal\":\"LUNCH\",\"recipeId\":" + recipe
                + ",\"amountGrams\":100}").statusCode()).isEqualTo(404);
        assertThat(post("/api/diary", bob, "{\"date\":\"" + TODAY + "\",\"meal\":\"LUNCH\",\"ingredientId\":" + food
                + ",\"amountGrams\":100}").statusCode()).isEqualTo(404);
        assertThat(post("/api/recipes", bob, "{\"name\":\"Stolen\",\"ingredients\":[{\"ingredientId\":" + food
                + ",\"amountGrams\":100}]}").statusCode()).isEqualTo(404);
        assertThat(delete("/api/water/" + water, bob).statusCode()).isEqualTo(404);
        assertThat(delete("/api/weight/" + weight, bob).statusCode()).isEqualTo(404);
        assertThat(get("/api/ingredients?scope=all&q=Secret", bob).body()).doesNotContain("Secret sauce");
        assertThat(get("/api/recipes?scope=all&q=Secret", bob).body()).doesNotContain("Secret stew");
        assertThat(get("/api/summary?date=" + TODAY, bob).body()).doesNotContain("soup");

        // ...and all of it is still intact for its owner.
        assertThat(get("/api/summary?date=" + TODAY, alice).body()).contains("soup");
        assertThat(get("/api/ingredients/" + food, alice).statusCode()).isEqualTo(200);
    }
}
