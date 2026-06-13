package com.calorietracker.backendcore.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user-defined (or public) recipe composed of multiple ingredients.
 * Total macros are cached on the row so listing recipes does not require
 * recomputing from line items.
 */
@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer servings = 1;

    @Column(name = "total_kcal", nullable = false)
    private Integer totalKcal = 0;

    @Column(name = "total_protein", nullable = false)
    private Double totalProtein = 0.0;

    @Column(name = "total_carbs", nullable = false)
    private Double totalCarbs = 0.0;

    @Column(name = "total_fat", nullable = false)
    private Double totalFat = 0.0;

    @Column(name = "total_fiber", nullable = false)
    private Double totalFiber = 0.0;

    @Column(name = "owner_user_id")
    private Long ownerUserId; // null = public

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
