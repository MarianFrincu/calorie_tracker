package com.calorietracker.backendcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A snapshot of the user's objective starting on {@code effectiveDate}. Saving
 * the objective today either inserts or updates the row for today; previous
 * days' rows are untouched so day reports stay anchored to the target that
 * was in effect at the time.
 */
@Entity
@Table(name = "objective_history",
       uniqueConstraints = @UniqueConstraint(name = "uniq_oh_user_date",
                                             columnNames = { "user_id", "effective_date" }))
@Getter
@Setter
@NoArgsConstructor
public class ObjectiveHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Goal goal;

    @Column(name = "goal_percent", nullable = false)
    private Integer goalPercent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "macro_preset", nullable = false)
    private MacroPreset macroPreset;

    @Column(name = "daily_calorie_target")
    private Integer dailyCalorieTarget;

    @Column(name = "daily_protein_target_g")
    private Integer dailyProteinTargetG;

    @Column(name = "daily_carbs_target_g")
    private Integer dailyCarbsTargetG;

    @Column(name = "daily_fat_target_g")
    private Integer dailyFatTargetG;

    @Column(name = "daily_fiber_target_g", nullable = false)
    private Integer dailyFiberTargetG = 30;

    @Column(name = "daily_water_target_ml", nullable = false)
    private Integer dailyWaterTargetMl = 2000;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
