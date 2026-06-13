package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.ObjectiveHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObjectiveHistoryRepository extends JpaRepository<ObjectiveHistory, Long> {

    Optional<ObjectiveHistory> findByUserIdAndEffectiveDate(Long userId, LocalDate effectiveDate);

    /** The objective in effect on the given date (the latest snapshot with effectiveDate <= date). */
    @Query("""
           SELECT o FROM ObjectiveHistory o
           WHERE o.userId = :userId AND o.effectiveDate <= :date
           ORDER BY o.effectiveDate DESC
           LIMIT 1
           """)
    Optional<ObjectiveHistory> findEffectiveOn(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** Earliest snapshot ever recorded for this user. Used to extend objectives backward
     *  to dates that predate the first save (so past days never get rewritten by a later save). */
    Optional<ObjectiveHistory> findFirstByUserIdOrderByEffectiveDateAsc(Long userId);

    /** All snapshots inside a date range, ordered by date asc. Useful for charts. */
    List<ObjectiveHistory> findByUserIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            Long userId, LocalDate from, LocalDate to);
}
