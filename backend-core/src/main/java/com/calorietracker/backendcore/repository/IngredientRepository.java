package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.Ingredient;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /** Owner-only - the "My ingredients" tab. New accounts start empty. */
    @Query("""
           SELECT i FROM Ingredient i
           WHERE i.ownerUserId = :userId
             AND LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY LOWER(i.name)
           """)
    Page<Ingredient> searchMine(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    /** Owner + public - used when adding to diary or building a recipe. */
    @Query("""
           SELECT i FROM Ingredient i
           WHERE (i.ownerUserId IS NULL OR i.ownerUserId = :userId)
             AND LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY (CASE WHEN i.ownerUserId = :userId THEN 0 ELSE 1 END), LOWER(i.name)
           """)
    Page<Ingredient> searchAll(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    /** How many recipe line-items still reference the given ingredient. */
    @Query("SELECT COUNT(ri) FROM RecipeIngredient ri WHERE ri.ingredient.id = :ingredientId")
    long countUsesOfIngredient(@Param("ingredientId") Long ingredientId);

    /** Whole user-visible pool (owner + public) - used by the fuzzy-search fallback. */
    @Query("SELECT i FROM Ingredient i WHERE i.ownerUserId IS NULL OR i.ownerUserId = :userId")
    List<Ingredient> findAllVisible(@Param("userId") Long userId);

    /** Owner-only pool - used by the fuzzy-search fallback on the My Library tab. */
    List<Ingredient> findByOwnerUserId(Long ownerUserId);
}
