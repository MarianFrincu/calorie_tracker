package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Body stats, BMR/TDEE, objectives and their per-day history. */
class ProfileObjectiveIntegrationTest extends IntegrationTest {

    @Test
    void aNewUserStartsWithAnEmptyProfileAndDefaultObjective() throws Exception {
        String t = newUser();
        JsonNode p = json(get("/api/profile", t), 200);
        assertThat(p.get("sex").isNull()).isTrue();
        assertThat(p.get("bmr").isNull()).isTrue();
        JsonNode o = json(get("/api/objective", t), 200);
        assertThat(o.get("goalPercent").asInt()).isZero();
        assertThat(o.get("macroPreset").asString()).isEqualTo("BALANCED");
        assertThat(o.get("dailyWaterTargetMl").asInt()).isEqualTo(2000);
        assertThat(o.get("dailyFiberTargetG").asInt()).isEqualTo(30);
        assertThat(o.get("dailyCalorieTarget").isNull()).isTrue();
    }

    @Test
    void savingTheProfileComputesBmrTdeeAndAMaintenanceObjective() throws Exception {
        String t = userWithProfile();
        JsonNode p = json(get("/api/profile", t), 200);
        assertThat(p.get("bmr").asInt()).isEqualTo(1780);
        assertThat(p.get("tdeeMaintain").asInt()).isEqualTo(2759);
        JsonNode o = json(get("/api/objective", t), 200);
        assertThat(o.get("dailyCalorieTarget").asInt()).isEqualTo(2759);
        // 30% protein would be 207 g = 2.6 g/kg: capped at 2.2 g/kg (176 g), the rest to carbs + fat.
        assertThat(o.get("dailyProteinTargetG").asInt()).isEqualTo(176);
        assertThat(o.get("dailyCarbsTargetG").asInt()).isEqualTo(367);
        assertThat(o.get("dailyFatTargetG").asInt()).isEqualTo(65);
    }

    @Test
    void profileUpdatesArePartialAndRecomputeTargets() throws Exception {
        String t = userWithProfile();
        JsonNode p = json(put("/api/profile", t, "{\"age\":40,\"displayName\":\"Maria\"}"), 200);
        assertThat(p.get("displayName").asString()).isEqualTo("Maria");
        assertThat(p.get("sex").asString()).isEqualTo("MALE"); // untouched
        assertThat(p.get("bmr").asInt()).isEqualTo(1730);    // 10 years older: -50
        assertThat(json(get("/api/objective", t), 200).get("dailyCalorieTarget").asInt())
                .isEqualTo((int) Math.round(1730 * 1.55));
    }

    @Test
    void femaleFormulaAndActivityMultiplier() throws Exception {
        String t = newUser();
        JsonNode p = json(put("/api/profile", t,
                "{\"sex\":\"FEMALE\",\"age\":30,\"heightCm\":165,\"weightKg\":60,\"activityLevel\":\"SEDENTARY\"}"), 200);
        assertThat(p.get("bmr").asInt()).isEqualTo(1320);
        assertThat(p.get("tdeeMaintain").asInt()).isEqualTo(1584);
    }

    @Test
    void objectiveGoalPresetAndStandaloneTargets() throws Exception {
        String t = userWithProfile();
        JsonNode o = json(put("/api/objective", t,
                "{\"goal\":\"LOSE\",\"goalPercent\":20,\"macroPreset\":\"MAINTAIN_MUSCLE\",\"dailyWaterTargetMl\":2500,\"dailyFiberTargetG\":35}"), 200);
        assertThat(o.get("dailyCalorieTarget").asInt()).isEqualTo(2207);
        assertThat(o.get("dailyProteinTargetG").asInt()).isEqualTo(176); // capped (80 kg x 2.2)
        assertThat(o.get("dailyCarbsTargetG").asInt()).isEqualTo(231);
        assertThat(o.get("dailyFatTargetG").asInt()).isEqualTo(64);
        assertThat(o.get("dailyWaterTargetMl").asInt()).isEqualTo(2500);
        assertThat(o.get("dailyFiberTargetG").asInt()).isEqualTo(35);

        JsonNode gain = json(put("/api/objective", t, "{\"goal\":\"GAIN\",\"goalPercent\":10,\"macroPreset\":\"KETOGENIC\"}"), 200);
        assertThat(gain.get("dailyCalorieTarget").asInt()).isEqualTo(3035);
        assertThat(gain.get("dailyCarbsTargetG").asInt()).isEqualTo(39);   // 5% of 3035 / 4, + a share of the capped protein
        assertThat(gain.get("dailyWaterTargetMl").asInt()).isEqualTo(2500); // kept from before
    }

    @Test
    void aLosingTargetIsNeverBelowBmr() throws Exception {
        String t = newUser();
        put("/api/profile", t,
                "{\"sex\":\"FEMALE\",\"age\":30,\"heightCm\":165,\"weightKg\":60,\"activityLevel\":\"SEDENTARY\"}");
        // TDEE 1584; cutting 30% would mean 1109 kcal - below the 1320 kcal BMR, so the target is the BMR.
        JsonNode o = json(put("/api/objective", t, "{\"goal\":\"LOSE\",\"goalPercent\":30}"), 200);
        assertThat(o.get("dailyCalorieTarget").asInt()).isEqualTo(1320);
    }

    @Test
    void changingTodaysObjectiveLeavesPastDaysAlone() throws Exception {
        String t = userWithProfile();
        put("/api/objective", t, "{\"goal\":\"LOSE\",\"goalPercent\":20}");
        assertThat(json(get("/api/summary?date=" + TODAY, t), 200).get("dailyCalorieTarget").asInt()).isEqualTo(2207);
        // Days before the first save inherit the implicit maintenance baseline.
        assertThat(json(get("/api/summary?date=" + YESTERDAY, t), 200).get("dailyCalorieTarget").asInt()).isEqualTo(2759);
    }

    @Test
    void todayFollowsTheClientsTimeZone() throws Exception {
        String t = newUser();
        String kiritimatiToday = java.time.LocalDate.now(java.time.ZoneId.of("Pacific/Kiritimati")).toString();
        var r = send("PUT", "/api/profile", t,
                "{\"sex\":\"MALE\",\"age\":30,\"heightCm\":180,\"weightKg\":80,\"activityLevel\":\"MODERATE\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://localhost:" + port + "/api/objective"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + t)
                .header("X-Time-Zone", "Pacific/Kiritimati")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString("{\"goal\":\"LOSE\",\"goalPercent\":10}"))
                .build();
        assertThat(HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        String sub = com.calorietracker.backendcore.support.TestJwts.subjectOf(t);
        assertThat(jdbc.queryForList("select effective_date::text from objective_history h join app_users u "
                + "on u.id = h.user_id where u.user_key = ? and h.goal = 'LOSE'", String.class, sub))
                .containsExactly(kiritimatiToday);
    }
}
