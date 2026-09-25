package db.migration;

import com.calorietracker.backendcore.model.ActivityLevel;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import com.calorietracker.backendcore.model.Sex;
import com.calorietracker.backendcore.service.NutritionCalculator;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * One-time: re-derives every user's current targets with the protein cap
 * (2.2 g/kg) and the BMR floor, using the same NutritionCalculator as the
 * app. Changed targets apply from today (UTC) on - a snapshot for today is
 * written, like a normal objective change - and past days keep their
 * historical targets. Users whose targets don't change are left alone.
 */
public class V8__ApplyProteinCapAndBmrFloor extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        Date today = Date.valueOf(LocalDate.now(ZoneOffset.UTC));
        try (PreparedStatement select = c.prepareStatement("""
                SELECT id, sex, age, height_cm, weight_kg, activity_level, goal, goal_percent, macro_preset,
                       daily_calorie_target, daily_protein_target_g, daily_carbs_target_g, daily_fat_target_g,
                       daily_fiber_target_g, daily_water_target_ml
                  FROM app_users
                 WHERE sex IS NOT NULL AND age IS NOT NULL AND height_cm IS NOT NULL
                   AND weight_kg IS NOT NULL AND activity_level IS NOT NULL
                """);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                AppUser u = new AppUser();
                u.setSex(Sex.valueOf(rs.getString("sex")));
                u.setAge(rs.getInt("age"));
                u.setHeightCm(rs.getDouble("height_cm"));
                u.setWeightKg(rs.getDouble("weight_kg"));
                u.setActivityLevel(ActivityLevel.valueOf(rs.getString("activity_level")));
                Goal goal = rs.getString("goal") == null ? Goal.MAINTAIN : Goal.valueOf(rs.getString("goal"));
                int percent = rs.getInt("goal_percent");
                MacroPreset preset = MacroPreset.valueOf(rs.getString("macro_preset"));

                Integer kcal = NutritionCalculator.dailyCalorieTarget(u, goal, percent);
                NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(kcal, preset, u.getWeightKg());
                boolean unchanged = Objects.equals(kcal, (Integer) rs.getObject("daily_calorie_target"))
                        && Objects.equals(g.protein(), (Integer) rs.getObject("daily_protein_target_g"))
                        && Objects.equals(g.carbs(), (Integer) rs.getObject("daily_carbs_target_g"))
                        && Objects.equals(g.fat(), (Integer) rs.getObject("daily_fat_target_g"));
                if (unchanged) continue;

                long id = rs.getLong("id");
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE app_users SET daily_calorie_target = ?, daily_protein_target_g = ?,
                               daily_carbs_target_g = ?, daily_fat_target_g = ?
                         WHERE id = ?
                        """)) {
                    update.setInt(1, kcal);
                    update.setInt(2, g.protein());
                    update.setInt(3, g.carbs());
                    update.setInt(4, g.fat());
                    update.setLong(5, id);
                    update.executeUpdate();
                }
                try (PreparedStatement snapshot = c.prepareStatement("""
                        INSERT INTO objective_history (user_id, effective_date, goal, goal_percent, macro_preset,
                               daily_calorie_target, daily_protein_target_g, daily_carbs_target_g, daily_fat_target_g,
                               daily_fiber_target_g, daily_water_target_ml)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (user_id, effective_date) DO UPDATE SET
                               goal = EXCLUDED.goal, goal_percent = EXCLUDED.goal_percent,
                               macro_preset = EXCLUDED.macro_preset,
                               daily_calorie_target = EXCLUDED.daily_calorie_target,
                               daily_protein_target_g = EXCLUDED.daily_protein_target_g,
                               daily_carbs_target_g = EXCLUDED.daily_carbs_target_g,
                               daily_fat_target_g = EXCLUDED.daily_fat_target_g,
                               daily_fiber_target_g = EXCLUDED.daily_fiber_target_g,
                               daily_water_target_ml = EXCLUDED.daily_water_target_ml
                        """)) {
                    snapshot.setLong(1, id);
                    snapshot.setDate(2, today);
                    snapshot.setString(3, goal.name());
                    snapshot.setInt(4, percent);
                    snapshot.setString(5, preset.name());
                    snapshot.setInt(6, kcal);
                    snapshot.setInt(7, g.protein());
                    snapshot.setInt(8, g.carbs());
                    snapshot.setInt(9, g.fat());
                    snapshot.setInt(10, rs.getInt("daily_fiber_target_g"));
                    snapshot.setInt(11, rs.getInt("daily_water_target_ml"));
                    snapshot.executeUpdate();
                }
            }
        }
    }
}
