package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.ObjectiveRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import com.calorietracker.backendcore.model.ObjectiveHistory;
import com.calorietracker.backendcore.repository.AppUserRepository;
import com.calorietracker.backendcore.repository.ObjectiveHistoryRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the user's objective. Updating the objective recomputes per-day targets
 * (calorie + macro grams) from the user's body stats and writes a snapshot to
 * {@link ObjectiveHistory} for today, leaving previous days untouched.
 */
@Service
public class ObjectiveService {

    /** Sentinel date used for the "implicit maintenance" baseline created when a
     *  user first completes their profile. Far enough in the past to be older
     *  than any realistic diary entry. The day-summary lookup walks backward to
     *  the latest snapshot &lt;= the queried date, so any past day picks this up. */
    private static final LocalDate BASELINE_DATE = LocalDate.of(2000, 1, 1);
    private static final int DEFAULT_FIBER_TARGET_G = 30;
    private static final int DEFAULT_WATER_TARGET_ML = 2000;

    private final AppUserRepository users;
    private final ObjectiveHistoryRepository history;

    public ObjectiveService(AppUserRepository users, ObjectiveHistoryRepository history) {
        this.users = users;
        this.history = history;
    }

    /**
     * If the user has a complete profile (so BMR/TDEE is computable) and no
     * baseline snapshot exists yet, write one at {@link #BASELINE_DATE} with
     * MAINTAIN goal + 0% + BALANCED preset + TDEE kcal. This is the "implicit
     * objective" that fills in past days before the user explicitly sets one.
     * Once written, never re-written — explicit objective changes only affect
     * today and forward, never the baseline.
     */
    @Transactional
    public void ensureBaselineHistory(AppUser me) {
        if (me.getSex() == null || me.getAge() == null || me.getHeightCm() == null
                || me.getWeightKg() == null || me.getActivityLevel() == null) {
            return;
        }
        if (history.findByUserIdAndEffectiveDate(me.getId(), BASELINE_DATE).isPresent()) {
            return;
        }
        Integer kcal = NutritionCalculator.dailyCalorieTarget(me, Goal.MAINTAIN, 0);
        if (kcal == null) return;

        NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(kcal, MacroPreset.BALANCED);
        ObjectiveHistory baseline = new ObjectiveHistory();
        baseline.setUserId(me.getId());
        baseline.setEffectiveDate(BASELINE_DATE);
        baseline.setGoal(Goal.MAINTAIN);
        baseline.setGoalPercent(0);
        baseline.setMacroPreset(MacroPreset.BALANCED);
        baseline.setDailyCalorieTarget(kcal);
        baseline.setDailyProteinTargetG(g.protein());
        baseline.setDailyCarbsTargetG(g.carbs());
        baseline.setDailyFatTargetG(g.fat());
        baseline.setDailyFiberTargetG(DEFAULT_FIBER_TARGET_G);
        baseline.setDailyWaterTargetMl(DEFAULT_WATER_TARGET_ML);
        history.save(baseline);
    }

    @Transactional
    public AppUser updateCurrent(AppUser me, ObjectiveRequest req) {
        if (req.goal() != null) me.setGoal(req.goal());
        if (req.goalPercent() != null) me.setGoalPercent(req.goalPercent());
        if (req.macroPreset() != null) me.setMacroPreset(req.macroPreset());
        if (req.dailyFiberTargetG() != null) me.setDailyFiberTargetG(req.dailyFiberTargetG());
        if (req.dailyWaterTargetMl() != null) me.setDailyWaterTargetMl(req.dailyWaterTargetMl());

        recomputeTargets(me);
        users.save(me);
        snapshotToHistory(me, LocalDate.now());
        return me;
    }

    /** Re-derives the cached daily targets from body + objective inputs. */
    @Transactional
    public void recomputeTargets(AppUser me) {
        if (me.getGoal() == null) me.setGoal(Goal.MAINTAIN);
        if (me.getMacroPreset() == null) me.setMacroPreset(MacroPreset.BALANCED);
        if (me.getGoalPercent() == null) me.setGoalPercent(0);

        Integer kcal = NutritionCalculator.dailyCalorieTarget(me, me.getGoal(), me.getGoalPercent());
        me.setDailyCalorieTarget(kcal);
        if (kcal != null) {
            NutritionCalculator.MacroGrams g = NutritionCalculator.macroGrams(kcal, me.getMacroPreset());
            me.setDailyProteinTargetG(g.protein());
            me.setDailyCarbsTargetG(g.carbs());
            me.setDailyFatTargetG(g.fat());
        } else {
            me.setDailyProteinTargetG(null);
            me.setDailyCarbsTargetG(null);
            me.setDailyFatTargetG(null);
        }
    }

    /** Upsert the snapshot for {@code date} from the user's current cached objective. */
    @Transactional
    public void snapshotToHistory(AppUser me, LocalDate date) {
        ObjectiveHistory snap = history.findByUserIdAndEffectiveDate(me.getId(), date)
                .orElseGet(() -> {
                    ObjectiveHistory h = new ObjectiveHistory();
                    h.setUserId(me.getId());
                    h.setEffectiveDate(date);
                    return h;
                });
        snap.setGoal(me.getGoal());
        snap.setGoalPercent(me.getGoalPercent());
        snap.setMacroPreset(me.getMacroPreset());
        snap.setDailyCalorieTarget(me.getDailyCalorieTarget());
        snap.setDailyProteinTargetG(me.getDailyProteinTargetG());
        snap.setDailyCarbsTargetG(me.getDailyCarbsTargetG());
        snap.setDailyFatTargetG(me.getDailyFatTargetG());
        snap.setDailyFiberTargetG(me.getDailyFiberTargetG());
        snap.setDailyWaterTargetMl(me.getDailyWaterTargetMl());
        history.save(snap);
    }

    /**
     * Objective in effect on the given date.
     * <p>
     * Returns the latest snapshot whose {@code effectiveDate <= date}. When the
     * date predates the user's <em>first</em> save, returns {@link Optional#empty()} —
     * the past is fixed and "no goal was set yet" must not be back-filled by today's
     * choices. Callers treat empty as zero targets for that day.
     */
    @Transactional(readOnly = true)
    public Optional<ObjectiveHistory> objectiveOn(Long userId, LocalDate date) {
        return history.findEffectiveOn(userId, date);
    }
}
