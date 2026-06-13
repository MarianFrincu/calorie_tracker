package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.ObjectiveRequest;
import com.calorietracker.backendcore.dto.ObjectiveResponse;
import com.calorietracker.backendcore.service.CurrentUserService;
import com.calorietracker.backendcore.service.ObjectiveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/objective")
public class ObjectiveController {

    private final CurrentUserService currentUser;
    private final ObjectiveService service;

    public ObjectiveController(CurrentUserService currentUser, ObjectiveService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    /** Returns the user's current cached objective. */
    @GetMapping
    public ObjectiveResponse me() {
        return ObjectiveResponse.of(currentUser.current());
    }

    /**
     * Update the current objective. Recomputes targets from body stats and
     * snapshots a new row into objective_history for today, leaving previous
     * days' snapshots intact.
     */
    @PutMapping
    public ObjectiveResponse update(@Valid @RequestBody ObjectiveRequest req) {
        return ObjectiveResponse.of(service.updateCurrent(currentUser.current(), req));
    }
}
