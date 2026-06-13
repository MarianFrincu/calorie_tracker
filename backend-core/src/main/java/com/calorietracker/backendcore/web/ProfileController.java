package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.ProfileResponse;
import com.calorietracker.backendcore.dto.UpdateProfileRequest;
import com.calorietracker.backendcore.service.CurrentUserService;
import com.calorietracker.backendcore.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final CurrentUserService currentUser;
    private final UserProfileService profileService;

    public ProfileController(CurrentUserService currentUser, UserProfileService profileService) {
        this.currentUser = currentUser;
        this.profileService = profileService;
    }

    /** Returns the current user's profile (auto-creates the row on first call). */
    @GetMapping
    public ProfileResponse me() {
        return ProfileResponse.of(currentUser.current());
    }

    /** Partial update - send only the fields you want to change. */
    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return ProfileResponse.of(profileService.updateCurrent(req));
    }
}
