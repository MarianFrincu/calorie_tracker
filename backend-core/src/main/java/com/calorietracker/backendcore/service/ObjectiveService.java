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

    private final AppUserRepository users;
    private final ObjectiveHistoryRepository history;

    public ObjectiveService(AppUserRepository users, ObjectiveHistoryRepository history) {
        this.users = users;
        this.history = history;
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
     * If a snapshot exists with {@code effectiveDate <= date}, use the latest such snapshot.
     * Otherwise (the date predates the user's first save), extend the <b>earliest</b> snapshot
     * backward. This is what makes the "past objectives never change" property hold:
     * once a snapshot is written, future edits update only today's row, so historical
     * lookups remain stable.
     */
    @Transactional(readOnly = true)
    public Optional<ObjectiveHistory> objectiveOn(Long userId, LocalDate date) {
        Optional<ObjectiveHistory> exact = history.findEffectiveOn(userId, date);
        if (exact.isPresent()) return exact;
        return history.findFirstByUserIdOrderByEffectiveDateAsc(userId);
    }
}
