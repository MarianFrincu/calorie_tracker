package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.DiaryEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    List<DiaryEntry> findByUserIdAndEntryDateOrderByMealAscIdAsc(Long userId, LocalDate entryDate);

    /** Single range scan for the report views (replaces a per-day loop). */
    List<DiaryEntry> findByUserIdAndEntryDateBetween(Long userId, LocalDate from, LocalDate to);
}
