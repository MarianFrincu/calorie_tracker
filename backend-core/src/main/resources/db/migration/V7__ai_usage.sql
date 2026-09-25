-- Per-user AI call counters (AiQuotaService). Kept in the database so the
-- limit is exact however many ai-service tasks are running.
CREATE TABLE ai_usage (
    user_id      BIGINT      NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    window_kind  VARCHAR(8)  NOT NULL,           -- MINUTE | DAY
    window_start TIMESTAMPTZ NOT NULL,
    calls        INT         NOT NULL,
    PRIMARY KEY (user_id, window_kind, window_start)
);
