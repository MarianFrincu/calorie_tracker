package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.UpdateProfileRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.repository.AppUserRepository;
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
    private final ClientClock clock;

    public UserProfileService(AppUserRepository repository,
                              CurrentUserService currentUser,
                              ObjectiveService objectives,
                              ClientClock clock) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.objectives = objectives;
        this.clock = clock;
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
            // First-time profile completion writes the "implicit maintenance"
            // baseline so past days inherit MAINTAIN + BALANCED + TDEE. Once
            // written it stays put — explicit objective edits won't touch it.
            objectives.ensureBaselineHistory(u);
            // New body stats also imply new targets for today; snapshot now.
            objectives.snapshotToHistory(u, clock.today());
        }
        return u;
    }

    /**
     * Permanently deletes the caller and everything they own. Diary, water,
     * weight, objective history, private ingredients and recipes all go via
     * ON DELETE CASCADE in the schema. The Cognito identity itself is deleted
     * by the client (DeleteUser needs the user's own access token).
     */
    @Transactional
    public void deleteCurrent() {
        Long id = currentUser.current().getId();
        repository.deleteRecipeLinesOwnedBy(id);
        repository.deleteById(id);
    }
}
