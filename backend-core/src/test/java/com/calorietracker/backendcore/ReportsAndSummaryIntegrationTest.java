package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The day summary card data and the weekly/monthly report series. */
class ReportsAndSummaryIntegrationTest extends IntegrationTest {

    @Test
    void summaryTotalsPerMealAndRemaining() throws Exception {
        String t = userWithProfile();
        post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"BREAKFAST\",\"ingredientId\":1,\"amountGrams\":100}");
        post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"DINNER\",\"customName\":\"Pizza\",\"customKcal\":800,"
                + "\"customProtein\":30,\"customCarbs\":90,\"customFat\":35,\"customFiber\":4}");
        post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":500}");

        JsonNode s = json(get("/api/summary?date=" + TODAY, t), 200);
        assertThat(s.get("consumedKcal").asInt()).isEqualTo(955);
        assertThat(s.get("dailyCalorieTarget").asInt()).isEqualTo(2759);
        assertThat(s.get("remainingKcal").asInt()).isEqualTo(2759 - 955);
        assertThat(s.get("totalProtein").asDouble()).isEqualTo(43.0);
        assertThat(s.get("totalFiber").asDouble()).isEqualTo(4.0);
        assertThat(s.get("byMeal").get("BREAKFAST").get("kcal").asInt()).isEqualTo(155);
        assertThat(s.get("byMeal").get("DINNER").get("entries").size()).isEqualTo(1);
        assertThat(s.get("byMeal").get("LUNCH").get("entries").size()).isZero();
        assertThat(s.get("water").get("totalMl").asInt()).isEqualTo(500);
        assertThat(s.get("water").get("targetMl").asInt()).isEqualTo(2000);
    }

    @Test
    void summaryWithoutAProfileHasNoTarget() throws Exception {
        JsonNode s = json(get("/api/summary?date=" + TODAY, newUser()), 200);
        assertThat(s.get("dailyCalorieTarget").asInt()).isZero();
        assertThat(s.get("remainingKcal").isNull()).isTrue();
    }

    @Test
    void nutritionReportHasOnePointPerDayWithThatDaysTarget() throws Exception {
        String t = userWithProfile();
        String from = LocalDate.now().minusDays(6).toString();
        post("/api/diary", t, "{\"date\":\"" + YESTERDAY + "\",\"meal\":\"LUNCH\",\"customName\":\"Soup\",\"customKcal\":300}");
        post("/api/diary", t, "{\"date\":\"" + TODAY + "\",\"meal\":\"LUNCH\",\"customName\":\"Salad\",\"customKcal\":200}");
        put("/api/objective", t, "{\"goal\":\"LOSE\",\"goalPercent\":20}");

        JsonNode days = json(get("/api/reports/nutrition?from=" + from + "&to=" + TODAY, t), 200);
        assertThat(days.size()).isEqualTo(7);
        JsonNode y = days.get(5);
        JsonNode today = days.get(6);
        assertThat(y.get("date").asString()).isEqualTo(YESTERDAY);
        assertThat(y.get("kcal").asInt()).isEqualTo(300);
        assertThat(y.get("kcalTarget").asInt()).isEqualTo(2759);   // yesterday's objective
        assertThat(today.get("kcal").asInt()).isEqualTo(200);
        assertThat(today.get("kcalTarget").asInt()).isEqualTo(2207); // today's new objective
        assertThat(days.get(0).get("kcal").asInt()).isZero();

        // Default range: the last 7 days.
        assertThat(json(get("/api/reports/nutrition", t), 200).size()).isEqualTo(7);
    }

    @Test
    void waterReportSumsEachDay() throws Exception {
        String t = userWithProfile();
        post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":250}");
        post("/api/water", t, "{\"date\":\"" + TODAY + "\",\"ml\":750}");
        JsonNode days = json(get("/api/reports/water?from=" + YESTERDAY + "&to=" + TODAY, t), 200);
        assertThat(days.size()).isEqualTo(2);
        assertThat(days.get(1).get("ml").asInt()).isEqualTo(1000);
        assertThat(days.get(1).get("mlTarget").asInt()).isEqualTo(2000);
        assertThat(days.get(0).get("ml").asInt()).isZero();
    }

    @Test
    void reportRangesAreValidated() throws Exception {
        String t = newUser();
        assertThat(get("/api/reports/nutrition?from=" + TODAY + "&to=" + YESTERDAY, t).statusCode()).isEqualTo(400);
        String longAgo = LocalDate.now().minusDays(400).toString();
        assertThat(json(get("/api/reports/water?from=" + longAgo + "&to=" + TODAY, t), 400).get("message").asString())
                .contains("max 366");
        String yearAgo = LocalDate.now().minusDays(365).toString();
        assertThat(json(get("/api/reports/nutrition?from=" + yearAgo + "&to=" + TODAY, t), 200).size()).isEqualTo(366);
    }
}
