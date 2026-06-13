package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.WaterEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterEntryRepository extends JpaRepository<WaterEntry, Long> {

    List<WaterEntry> findByUserIdAndEntryDateOrderByCreatedAtAsc(Long userId, LocalDate entryDate);

    /** Single range scan for the report views. */
    List<WaterEntry> findByUserIdAndEntryDateBetween(Long userId, LocalDate from, LocalDate to);
}
