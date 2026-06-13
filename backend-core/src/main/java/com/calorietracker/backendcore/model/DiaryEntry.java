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
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One logged item in a user's day, in a specific meal slot. Name and macros
 * are denormalized at save time so editing/deleting the source ingredient or
 * recipe later never alters history.
 */
@Entity
@Table(name = "diary_entries")
@Getter
@Setter
@NoArgsConstructor
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Meal meal;

    @Column(name = "ingredient_id")
    private Long ingredientId; // optional FK (set when added by picking an ingredient)

    @Column(name = "recipe_id")
    private Long recipeId;     // optional FK (set when added by picking a recipe)

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "amount_text")
    private String amountText;

    @Column(nullable = false)
    private Integer kcal;

    @Column(nullable = false)
    private Double protein;

    @Column(nullable = false)
    private Double carbs;

    @Column(nullable = false)
    private Double fat;

    @Column(nullable = false)
    private Double fiber = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
