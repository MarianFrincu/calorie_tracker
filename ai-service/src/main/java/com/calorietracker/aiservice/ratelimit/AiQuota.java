package com.calorietracker.aiservice.ratelimit;

/** The per-user AI quota. Checked by the controller after request validation. */
public interface AiQuota {

    /**
     * Counts one AI call for the current user.
     *
     * @throws RateLimitExceededException when the user is over quota
     */
    void consume();
}
