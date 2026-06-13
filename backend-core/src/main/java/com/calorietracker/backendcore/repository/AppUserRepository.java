package com.calorietracker.backendcore.repository;

import com.calorietracker.backendcore.model.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserKey(String userKey);
}
