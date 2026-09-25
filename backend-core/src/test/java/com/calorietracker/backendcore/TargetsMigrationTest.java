package com.calorietracker.backendcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.calorietracker.backendcore.support.TestJwts;
import db.migration.V8__ApplyProteinCapAndBmrFloor;
import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** V8 re-derives targets saved before the protein cap / BMR floor existed. */
class TargetsMigrationTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void oldUncappedTargetsAreRecomputedFromToday() throws Exception {
        String t = userWithProfile(); // 80 kg, maintenance, balanced
        long id = jdbc.queryForObject("SELECT id FROM app_users WHERE user_key = ?", Long.class,
                TestJwts.subjectOf(t));
        // What the app stored before the cap: 30% protein = 207 g (2.6 g/kg).
        jdbc.update("UPDATE app_users SET daily_protein_target_g = 207, daily_carbs_target_g = 345, "
                + "daily_fat_target_g = 61 WHERE id = ?", id);

        try (Connection c = dataSource.getConnection()) {
            new V8__ApplyProteinCapAndBmrFloor().migrate(new Context() {
                @Override public Configuration getConfiguration() { return null; }
                @Override public Connection getConnection() { return c; }
            });
        }

        JsonNode o = json(get("/api/objective", t), 200);
        assertThat(o.get("dailyProteinTargetG").asInt()).isEqualTo(176);
        assertThat(o.get("dailyCarbsTargetG").asInt()).isEqualTo(367);
        assertThat(o.get("dailyFatTargetG").asInt()).isEqualTo(65);
        assertThat(json(get("/api/summary?date=" + TODAY, t), 200).get("proteinTargetG").asInt()).isEqualTo(176);
    }
}
