package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.Recipe;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** Owner-only - the "My recipes" tab. New accounts start empty. */
    @Query("""
           SELECT r FROM Recipe r
           WHERE r.ownerUserId = :userId
             AND LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY LOWER(r.name)
           """)
    Page<Recipe> searchMine(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    /** Owner + public - used when picking a recipe to add to the diary. */
    @Query("""
           SELECT r FROM Recipe r
           WHERE (r.ownerUserId IS NULL OR r.ownerUserId = :userId)
             AND LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY (CASE WHEN r.ownerUserId = :userId THEN 0 ELSE 1 END), LOWER(r.name)
           """)
    Page<Recipe> searchAll(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    /** How many recipe line-items still reference the given ingredient. */
    @Query("SELECT COUNT(ri) FROM RecipeIngredient ri WHERE ri.ingredient.id = :ingredientId")
    long countUsesOfIngredient(@Param("ingredientId") Long ingredientId);

    /** Whole user-visible pool (owner + public) - used by the fuzzy-search fallback. */
    @Query("SELECT r FROM Recipe r WHERE r.ownerUserId IS NULL OR r.ownerUserId = :userId")
    List<Recipe> findAllVisible(@Param("userId") Long userId);

    /** Owner-only pool - used by the fuzzy-search fallback on the My Library tab. */
    List<Recipe> findByOwnerUserId(Long ownerUserId);
}
