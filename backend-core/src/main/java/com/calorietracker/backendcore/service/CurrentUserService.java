package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves "who is calling" and lazily creates the matching {@link AppUser}.
 *
 * - cognito profile  -> userKey = JWT 'sub'
 * - dev profile      -> userKey = "dev-user" (single shared local account)
 *
 * Lazy onboarding means the first call from a new user automatically creates
 * the row with default settings - no separate sign-up endpoint needed.
 */
@Service
public class CurrentUserService {

    public static final String DEV_USER_KEY = "dev-user";

    private final AppUserRepository repository;

    public CurrentUserService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppUser current() {
        String userKey = resolveUserKey();
        return repository.findByUserKey(userKey).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUserKey(userKey);
            u.setDisplayName(userKey.equals(DEV_USER_KEY) ? "Local Dev" : userKey);
            return repository.save(u);
        });
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
