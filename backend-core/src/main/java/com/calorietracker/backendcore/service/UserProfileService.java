package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.UpdateProfileRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.repository.AppUserRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Body stats only (the objective lives in {@link ObjectiveService}). Any body
 * change that affects BMR/TDEE also re-derives the cached daily targets and
 * snapshots a new objective row for today.
 */
@Service
public class UserProfileService {

    private final AppUserRepository repository;
    private final CurrentUserService currentUser;
    private final ObjectiveService objectives;

    public UserProfileService(AppUserRepository repository,
                              CurrentUserService currentUser,
                              ObjectiveService objectives) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.objectives = objectives;
    }

    @Transactional
    public AppUser updateCurrent(UpdateProfileRequest req) {
        AppUser u = currentUser.current();
        boolean bodyChanged = false;
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (req.sex() != null) { u.setSex(req.sex()); bodyChanged = true; }
        if (req.age() != null) { u.setAge(req.age()); bodyChanged = true; }
        if (req.heightCm() != null) { u.setHeightCm(req.heightCm()); bodyChanged = true; }
        if (req.weightKg() != null) { u.setWeightKg(req.weightKg()); bodyChanged = true; }
        if (req.activityLevel() != null) { u.setActivityLevel(req.activityLevel()); bodyChanged = true; }

        if (bodyChanged) {
            objectives.recomputeTargets(u);
        }
        repository.save(u);
        if (bodyChanged) {
            // New body stats imply new targets; snapshot for today.
            objectives.snapshotToHistory(u, LocalDate.now());
        }
        return u;
    }
}
