package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.DaySummaryResponse;
import com.calorietracker.backendcore.dto.DaySummaryResponse.MealBlock;
import com.calorietracker.backendcore.dto.DaySummaryResponse.WaterSummary;
import com.calorietracker.backendcore.dto.DiaryEntryResponse;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.DiaryEntry;
import com.calorietracker.backendcore.model.Meal;
import com.calorietracker.backendcore.model.ObjectiveHistory;
import com.calorietracker.backendcore.model.WaterEntry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the day view payload (diary by meal + totals + per-day targets +
 * water). All targets come from the objective in effect on that specific date
 * (via {@link ObjectiveService#objectiveOn}), so navigating to yesterday shows
 * yesterday's frozen targets even after today's objective was changed.
 */
@Service
public class SummaryService {

    private final DiaryService diary;
    private final WaterService water;
    private final CurrentUserService currentUser;
    private final ObjectiveService objectives;

    public SummaryService(DiaryService diary, WaterService water,
                          CurrentUserService currentUser, ObjectiveService objectives) {
        this.diary = diary;
        this.water = water;
        this.currentUser = currentUser;
        this.objectives = objectives;
    }

    @Transactional(readOnly = true)
    public DaySummaryResponse forDate(LocalDate date) {
        AppUser me = currentUser.current();

        Map<Meal, MealBuilder> byMeal = new EnumMap<>(Meal.class);
        for (Meal m : Meal.values()) byMeal.put(m, new MealBuilder());

        int totalKcal = 0;
        double totalP = 0, totalC = 0, totalF = 0, totalFi = 0;
        for (DiaryEntry e : diary.byDate(date)) {
            MealBuilder b = byMeal.get(e.getMeal());
            b.entries.add(DiaryEntryResponse.of(e));
            b.kcal    += e.getKcal();
            b.protein += e.getProtein();
            b.carbs   += e.getCarbs();
            b.fat     += e.getFat();
            b.fiber   += e.getFiber();
            totalKcal += e.getKcal();
            totalP    += e.getProtein();
            totalC    += e.getCarbs();
            totalF    += e.getFat();
            totalFi   += e.getFiber();
        }

        Map<Meal, MealBlock> out = new EnumMap<>(Meal.class);
        for (Map.Entry<Meal, MealBuilder> en : byMeal.entrySet()) {
            MealBuilder b = en.getValue();
            out.put(en.getKey(), new MealBlock(b.kcal,
                    round1(b.protein), round1(b.carbs), round1(b.fat), round1(b.fiber),
                    b.entries));
        }

        List<WaterEntry> waterRows = water.byDate(date);
        int totalMl = waterRows.stream().mapToInt(WaterEntry::getMl).sum();
        List<DaySummaryResponse.WaterEntryResponse> waterDtos = waterRows.stream()
                .map(w -> new DaySummaryResponse.WaterEntryResponse(w.getId(), w.getMl()))
                .toList();

        // Resolve the objective in effect on the requested date; fall back to
        // the user's current cached targets if no snapshot exists yet (typical
        // for a brand-new user who hasn't saved an objective yet).
        ObjectiveHistory obj = objectives.objectiveOn(me.getId(), date).orElse(null);
        Integer target        = obj == null ? me.getDailyCalorieTarget()    : obj.getDailyCalorieTarget();
        Integer waterTarget   = obj == null ? me.getDailyWaterTargetMl()    : obj.getDailyWaterTargetMl();
        Integer proteinTarget = obj == null ? me.getDailyProteinTargetG()   : obj.getDailyProteinTargetG();
        Integer carbsTarget   = obj == null ? me.getDailyCarbsTargetG()     : obj.getDailyCarbsTargetG();
        Integer fatTarget     = obj == null ? me.getDailyFatTargetG()       : obj.getDailyFatTargetG();
        Integer fiberTarget   = obj == null ? me.getDailyFiberTargetG()     : obj.getDailyFiberTargetG();
        Integer remaining = target == null ? null : target - totalKcal;

        return new DaySummaryResponse(
                date, target, totalKcal, remaining,
                round1(totalP), round1(totalC), round1(totalF), round1(totalFi),
                proteinTarget, carbsTarget, fatTarget, fiberTarget,
                out,
                new WaterSummary(waterTarget, totalMl, waterDtos));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class MealBuilder {
        int kcal;
        double protein, carbs, fat, fiber;
        final List<DiaryEntryResponse> entries = new ArrayList<>();
    }
}
