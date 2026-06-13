package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.DailyNutritionPoint;
import com.calorietracker.backendcore.dto.DailyWaterPoint;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.DiaryEntry;
import com.calorietracker.backendcore.model.ObjectiveHistory;
import com.calorietracker.backendcore.model.WaterEntry;
import com.calorietracker.backendcore.repository.DiaryEntryRepository;
import com.calorietracker.backendcore.repository.ObjectiveHistoryRepository;
import com.calorietracker.backendcore.repository.WaterEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates diary and water entries into per-day points for the chart views.
 * Targets come from the objective in effect on each day (so historical points
 * reflect the goal you had at the time, not whatever it is now).
 * <p>
 * Performance shape: each report request does at most <b>three</b> DB queries
 * regardless of range length (one diary range scan, one water range scan, one
 * objective-history range scan). Earlier the service issued one query per day,
 * which at the 366-day cap was over 700 round-trips.
 */
@Service
public class ReportService {

    private final DiaryEntryRepository diary;
    private final WaterEntryRepository water;
    private final ObjectiveHistoryRepository objectiveHistory;
    private final CurrentUserService currentUser;

    public ReportService(DiaryEntryRepository diary, WaterEntryRepository water,
                         ObjectiveHistoryRepository objectiveHistory, CurrentUserService currentUser) {
        this.diary = diary;
        this.water = water;
        this.objectiveHistory = objectiveHistory;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<DailyNutritionPoint> nutrition(LocalDate from, LocalDate to) {
        AppUser me = currentUser.current();
        Map<LocalDate, Totals> byDate = bucketDates(from, to, Totals::new);
        for (DiaryEntry e : diary.findByUserIdAndEntryDateBetween(me.getId(), from, to)) {
            byDate.computeIfAbsent(e.getEntryDate(), k -> new Totals()).add(e);
        }
        ObjectiveResolver objectives = resolveObjectivesFor(me, from, to);

        List<DailyNutritionPoint> out = new ArrayList<>();
        LocalDate d = from;
        while (!d.isAfter(to)) {
            Totals t = byDate.getOrDefault(d, new Totals());
            ObjectiveHistory obj = objectives.on(d);
            out.add(new DailyNutritionPoint(d,
                    t.kcal, round1(t.protein), round1(t.carbs), round1(t.fat), round1(t.fiber),
                    obj == null ? me.getDailyCalorieTarget()   : obj.getDailyCalorieTarget(),
                    obj == null ? me.getDailyProteinTargetG()  : obj.getDailyProteinTargetG(),
                    obj == null ? me.getDailyCarbsTargetG()    : obj.getDailyCarbsTargetG(),
                    obj == null ? me.getDailyFatTargetG()      : obj.getDailyFatTargetG(),
                    obj == null ? me.getDailyFiberTargetG()    : obj.getDailyFiberTargetG()));
            d = d.plusDays(1);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<DailyWaterPoint> water(LocalDate from, LocalDate to) {
        AppUser me = currentUser.current();
        Map<LocalDate, Integer> byDate = bucketDates(from, to, () -> 0);
        for (WaterEntry w : water.findByUserIdAndEntryDateBetween(me.getId(), from, to)) {
            byDate.merge(w.getEntryDate(), w.getMl(), Integer::sum);
        }
        ObjectiveResolver objectives = resolveObjectivesFor(me, from, to);

        List<DailyWaterPoint> out = new ArrayList<>();
        LocalDate d = from;
        while (!d.isAfter(to)) {
            ObjectiveHistory obj = objectives.on(d);
            Integer target = obj == null ? me.getDailyWaterTargetMl() : obj.getDailyWaterTargetMl();
            out.add(new DailyWaterPoint(d, byDate.getOrDefault(d, 0), target));
            d = d.plusDays(1);
        }
        return out;
    }

    /**
     * Pulls every objective snapshot inside [from, to] PLUS the single most
     * recent snapshot strictly before {@code from} (covers dates at the start
     * of the range that inherit a prior objective). One DB query feeds an
     * in-memory resolver used for all per-day lookups.
     */
    private ObjectiveResolver resolveObjectivesFor(AppUser me, LocalDate from, LocalDate to) {
        List<ObjectiveHistory> snapshots = new ArrayList<>(
                objectiveHistory.findByUserIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(me.getId(), from, to));
        objectiveHistory.findEffectiveOn(me.getId(), from.minusDays(1)).ifPresent(snapshots::add);
        objectiveHistory.findFirstByUserIdOrderByEffectiveDateAsc(me.getId())
                .filter(s -> snapshots.stream().noneMatch(x -> x.getEffectiveDate().equals(s.getEffectiveDate())))
                .ifPresent(snapshots::add);
        TreeMap<LocalDate, ObjectiveHistory> indexed = new TreeMap<>();
        for (ObjectiveHistory s : snapshots) indexed.put(s.getEffectiveDate(), s);
        return new ObjectiveResolver(indexed);
    }

    private static <V> Map<LocalDate, V> bucketDates(LocalDate from, LocalDate to,
                                                     java.util.function.Supplier<V> empty) {
        Map<LocalDate, V> out = new HashMap<>();
        LocalDate d = from;
        while (!d.isAfter(to)) {
            out.put(d, empty.get());
            d = d.plusDays(1);
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class Totals {
        int kcal;
        double protein, carbs, fat, fiber;
        void add(DiaryEntry e) {
            kcal    += e.getKcal();
            protein += e.getProtein();
            carbs   += e.getCarbs();
            fat     += e.getFat();
            fiber   += e.getFiber();
        }
    }

    /**
     * In-memory implementation of {@link ObjectiveService#objectiveOn} that
     * answers any number of day-lookups against a TreeMap built ONCE per
     * request, so an N-day report stays O(N log K) where K is the number of
     * objective revisions the user has ever made (typically &lt; 5).
     */
    private static final class ObjectiveResolver {
        private final TreeMap<LocalDate, ObjectiveHistory> byDate;

        ObjectiveResolver(TreeMap<LocalDate, ObjectiveHistory> byDate) {
            this.byDate = byDate;
        }

        ObjectiveHistory on(LocalDate date) {
            Map.Entry<LocalDate, ObjectiveHistory> e = byDate.floorEntry(date);
            if (e != null) return e.getValue();
            // date is before every known snapshot - extend the earliest backward.
            return byDate.isEmpty() ? null : byDate.firstEntry().getValue();
        }
    }
}
