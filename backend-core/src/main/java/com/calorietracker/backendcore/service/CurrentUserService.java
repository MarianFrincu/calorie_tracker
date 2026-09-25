package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves "who is calling" and lazily creates the matching {@link AppUser}.
 *
 * - cognito profile  -> userKey = JWT 'sub'
 * - dev profile      -> userKey = "dev-user" (single shared local account)
 *
 * Lazy onboarding means the first call from a new user automatically creates
 * the row with default settings - no separate sign-up endpoint needed. The
 * insert runs in its own transaction (see {@link UserProvisioner}), so it works
 * from read-only callers and when a client fires several first requests at once.
 */
@Service
public class CurrentUserService {

    public static final String DEV_USER_KEY = "dev-user";

    private final AppUserRepository repository;
    private final UserProvisioner provisioner;

    public CurrentUserService(AppUserRepository repository, UserProvisioner provisioner) {
        this.repository = repository;
        this.provisioner = provisioner;
    }

    /** Joins the caller's transaction (if any) so the returned entity is managed there. */
    public AppUser current() {
        String userKey = resolveUserKey();
        return repository.findByUserKey(userKey).orElseGet(() -> {
            provisioner.provision(userKey, userKey.equals(DEV_USER_KEY) ? "Local Dev" : userKey);
            return repository.findByUserKey(userKey)
                    .orElseThrow(() -> new IllegalStateException("Could not create user " + userKey));
        });
    }

    /** Stable external id of the caller, without touching the database. */
    public String currentUserKey() {
        return resolveUserKey();
    }

    private String resolveUserKey() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) {
            return DEV_USER_KEY;
        }
        if (a.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return a.getName();
    }
}
