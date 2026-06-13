package com.calorietracker.backendcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per app user. {@code userKey} is the stable external identifier:
 *  - In the cloud (cognito profile) it is the Cognito 'sub' claim of the JWT.
 *  - Locally (dev profile)          it is the literal string "dev-user".
 *
 * The {@code goal*} / {@code dailyXxx*} fields cache the CURRENT objective for
 * fast reads. Each save also writes an {@link ObjectiveHistory} snapshot so we
 * can answer "what was the objective on date D?" without losing history.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, unique = true)
    private String userKey;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    private Integer age;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    private ActivityLevel activityLevel;

    // --- objective cache (latest snapshot) ---

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Column(name = "goal_percent", nullable = false)
    private Integer goalPercent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "macro_preset", nullable = false)
    private MacroPreset macroPreset = MacroPreset.BALANCED;

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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (dailyWaterTargetMl == null) dailyWaterTargetMl = 2000;
        if (dailyFiberTargetG == null) dailyFiberTargetG = 30;
        if (goalPercent == null) goalPercent = 0;
        if (macroPreset == null) macroPreset = MacroPreset.BALANCED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
