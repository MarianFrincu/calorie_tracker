package com.calorietracker.desktop.api;

/**
 * The signed-in user's Cognito tokens, kept fresh.
 *
 * <p>{@link ApiClient} asks for {@link #accessToken()} on every request and
 * calls {@link #refresh()} when the API answers 401. Refreshing uses the
 * 30-day refresh token, so an expired 1-hour access token no longer ends the
 * session mid-use.
 */
public class AuthSession {

    private final CognitoAuthService cognito;
    private String accessToken;
    private String refreshToken;
    private long refreshIssuedAt;

    public AuthSession(CognitoAuthService cognito) {
        this.cognito = cognito;
    }

    public CognitoAuthService cognito() {
        return cognito;
    }

    /** After a password sign-in: adopt the new tokens and persist them. */
    public synchronized void signedIn(CognitoAuthService.Tokens tokens) {
        accessToken = tokens.accessToken();
        refreshToken = tokens.refreshToken();
        refreshIssuedAt = System.currentTimeMillis();
        SessionStore.save(tokens, refreshIssuedAt);
    }

    /**
     * Resume a session saved by an earlier launch.
     *
     * @return false when nothing usable was saved
     */
    public synchronized boolean restore() {
        SessionStore.Saved saved = SessionStore.load();
        if (saved == null) return false;
        long now = System.currentTimeMillis();
        accessToken = saved.accessValid(now) ? saved.accessToken() : null;
        refreshToken = saved.refreshValid(now) ? saved.refreshToken() : null;
        refreshIssuedAt = saved.refreshExpiresAt() - SessionStore.REFRESH_LIFETIME_MS;
        return accessToken != null || (refreshToken != null && refresh());
    }

    public synchronized String accessToken() {
        return accessToken;
    }

    public synchronized boolean isSignedIn() {
        return accessToken != null;
    }

    /**
     * Mint a new access token. Synchronized, so a burst of parallel 401s
     * triggers one refresh; the rest see the new token.
     *
     * @param rejected the token the caller just had rejected, or null to force a refresh
     * @return true if a usable access token is now available
     */
    public synchronized boolean refresh(String rejected) {
        if (rejected != null && accessToken != null && !accessToken.equals(rejected)) {
            return true; // someone else already refreshed while we waited
        }
        return refresh();
    }

    private boolean refresh() {
        if (refreshToken == null) return false;
        try {
            CognitoAuthService.Tokens fresh = cognito.refresh(refreshToken);
            accessToken = fresh.accessToken();
            SessionStore.save(fresh, refreshIssuedAt);
            return true;
        } catch (Exception e) {
            // Revoked, expired or the user was deleted: the session is over.
            clearLocal();
            return false;
        }
    }

    /** Sign out: revoke the refresh token at Cognito (best effort) and forget everything locally. */
    public void signOut() {
        String toRevoke;
        synchronized (this) {
            toRevoke = refreshToken;
            clearLocal();
        }
        if (toRevoke != null) {
            try {
                cognito.revoke(toRevoke);
            } catch (Exception ignored) {
                // Offline or already invalid; the local session is gone either way.
            }
        }
    }

    /** Drop tokens without contacting Cognito (session already dead, or account deleted). */
    public synchronized void clearLocal() {
        accessToken = null;
        refreshToken = null;
        SessionStore.clear();
    }
}
