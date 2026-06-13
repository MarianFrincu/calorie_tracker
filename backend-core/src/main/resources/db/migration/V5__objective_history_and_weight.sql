-- V5: separate Objective from Profile + daily Weight log.
--
-- app_users gains the new objective inputs (percent + macro preset + macro
-- target grams). The objective_history table snapshots that state per day so
-- changing today's objective never alters yesterday's report. The weight_log
-- table holds one row per user per day for the weight chart.

ALTER TABLE app_users
    ADD COLUMN goal_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN macro_preset VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
    ADD COLUMN daily_protein_target_g INT,
    ADD COLUMN daily_carbs_target_g   INT,
    ADD COLUMN daily_fat_target_g     INT,
    ADD COLUMN daily_fiber_target_g   INT NOT NULL DEFAULT 30;

CREATE TABLE objective_history (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    effective_date         DATE NOT NULL,
    goal                   VARCHAR(16) NOT NULL,                  -- LOSE | MAINTAIN | GAIN
    goal_percent           INT NOT NULL DEFAULT 0,                -- 0, 10, 15, 20, 25, 30
    macro_preset           VARCHAR(32) NOT NULL,                  -- BALANCED | MAINTAIN_MUSCLE | KETOGENIC
    daily_calorie_target   INT,
    daily_protein_target_g INT,
    daily_carbs_target_g   INT,
    daily_fat_target_g     INT,
    daily_fiber_target_g   INT NOT NULL DEFAULT 30,
    daily_water_target_ml  INT NOT NULL DEFAULT 2000,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uniq_oh_user_date UNIQUE (user_id, effective_date)
);
CREATE INDEX idx_oh_user_date ON objective_history(user_id, effective_date DESC);

CREATE TABLE weight_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    log_date    DATE NOT NULL,
    weight_kg   DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uniq_wl_user_date UNIQUE (user_id, log_date)
);
CREATE INDEX idx_wl_user_date ON weight_log(user_id, log_date);
