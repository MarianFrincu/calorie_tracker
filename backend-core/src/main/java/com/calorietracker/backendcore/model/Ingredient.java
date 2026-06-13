package com.calorietracker.backendcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A food ingredient. Macros are per 100 g of edible portion. When
 * {@code ownerUserId} is null, the ingredient is public (visible to all users).
 */
@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(name = "kcal_per_100g", nullable = false)
    private Integer kcalPer100g;

    @Column(name = "protein_per_100g", nullable = false)
    private Double proteinPer100g;

    @Column(name = "carbs_per_100g", nullable = false)
    private Double carbsPer100g;

    @Column(name = "fat_per_100g", nullable = false)
    private Double fatPer100g;

    @Column(name = "fiber_per_100g", nullable = false)
    private Double fiberPer100g = 0.0;

    @Column(name = "owner_user_id")
    private Long ownerUserId; // null = public

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
