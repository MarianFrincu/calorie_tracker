package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the {@code app_users} row for a first-time caller.
 *
 * <p>Lives in its own bean so the {@code REQUIRES_NEW} transaction actually
 * applies (a self-call would bypass the proxy). That matters because the
 * first request a new user makes is often a read - GET /api/summary - whose
 * transaction is read-only, and PostgreSQL rejects an INSERT inside one.
 */
@Service
class UserProvisioner {

    private final AppUserRepository repository;

    UserProvisioner(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provision(String userKey, String displayName) {
        repository.insertIfAbsent(userKey, displayName);
    }
}
