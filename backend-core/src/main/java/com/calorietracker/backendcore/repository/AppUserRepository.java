package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserKey(String userKey);

    /**
     * Creates the user row unless it already exists. Idempotent and safe under
     * concurrency: two first requests racing each other both succeed, one of
     * them simply inserts nothing. Every other column has a database default.
     */
    @Modifying
    @Query(value = """
           INSERT INTO app_users (user_key, display_name)
           VALUES (:userKey, :displayName)
           ON CONFLICT (user_key) DO NOTHING
           """, nativeQuery = true)
    int insertIfAbsent(@Param("userKey") String userKey, @Param("displayName") String displayName);

    /**
     * Recipe line items of the user's own recipes. They must go before the
     * user row: recipe_ingredients -> ingredients has no cascade (on purpose -
     * an ingredient in use must not vanish), so the account-level cascade
     * would otherwise trip over it.
     */
    @Modifying
    @Query(value = """
           DELETE FROM recipe_ingredients
           WHERE recipe_id IN (SELECT id FROM recipes WHERE owner_user_id = :userId)
           """, nativeQuery = true)
    int deleteRecipeLinesOwnedBy(@Param("userId") Long userId);
}
