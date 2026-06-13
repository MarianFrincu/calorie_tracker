package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.WeightLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightLogRepository extends JpaRepository<WeightLog, Long> {

    Optional<WeightLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);

    List<WeightLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate from, LocalDate to);

    List<WeightLog> findByUserIdOrderByLogDateAsc(Long userId);
}
