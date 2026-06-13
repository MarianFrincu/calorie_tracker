-- Calorie Tracker - initial schema.
-- Drops the old single-table demo and creates the per-user nutrition model.

DROP TABLE IF EXISTS food_entries CASCADE;

CREATE TABLE app_users (
    id                    BIGSERIAL PRIMARY KEY,
    user_key              VARCHAR(255) NOT NULL UNIQUE,  -- JWT 'sub' in cloud, 'dev-user' locally
    display_name          VARCHAR(255),
    sex                   VARCHAR(16),                   -- MALE | FEMALE
    age                   INT,
    height_cm             DOUBLE PRECISION,
    weight_kg             DOUBLE PRECISION,
    activity_level        VARCHAR(32),                   -- SEDENTARY | LIGHT | MODERATE | ACTIVE | VERY_ACTIVE
    goal                  VARCHAR(16),                   -- LOSE | MAINTAIN | GAIN
    daily_calorie_target  INT,                           -- computed via Mifflin-St Jeor + activity + goal
    daily_water_target_ml INT NOT NULL DEFAULT 2000,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- owner_user_id NULL = public ingredient/recipe visible to everyone.
CREATE TABLE ingredients (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    brand             VARCHAR(255),
    kcal_per_100g     INT NOT NULL,
    protein_per_100g  DOUBLE PRECISION NOT NULL,
    carbs_per_100g    DOUBLE PRECISION NOT NULL,
    fat_per_100g      DOUBLE PRECISION NOT NULL,
    owner_user_id     BIGINT REFERENCES app_users(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ingredients_owner ON ingredients(owner_user_id);
CREATE INDEX idx_ingredients_name_lower ON ingredients(LOWER(name));

CREATE TABLE recipes (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    servings        INT NOT NULL DEFAULT 1,
    total_kcal      INT NOT NULL DEFAULT 0,
    total_protein   DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_carbs     DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_fat       DOUBLE PRECISION NOT NULL DEFAULT 0,
    owner_user_id   BIGINT REFERENCES app_users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recipes_owner ON recipes(owner_user_id);

CREATE TABLE recipe_ingredients (
    id            BIGSERIAL PRIMARY KEY,
    recipe_id     BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id),
    amount_grams  DOUBLE PRECISION NOT NULL
);
CREATE INDEX idx_ri_recipe ON recipe_ingredients(recipe_id);

-- A diary entry is self-contained for display (denormalized name + macros) so
-- editing/deleting the source ingredient or recipe never alters history.
CREATE TABLE diary_entries (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    entry_date    DATE NOT NULL,
    meal          VARCHAR(16) NOT NULL,                  -- BREAKFAST | LUNCH | DINNER | SNACK
    ingredient_id BIGINT REFERENCES ingredients(id) ON DELETE SET NULL,
    recipe_id     BIGINT REFERENCES recipes(id) ON DELETE SET NULL,
    display_name  VARCHAR(255) NOT NULL,
    amount_text   VARCHAR(64),                            -- "150 g" or "1 serving"
    kcal          INT NOT NULL,
    protein       DOUBLE PRECISION NOT NULL,
    carbs         DOUBLE PRECISION NOT NULL,
    fat           DOUBLE PRECISION NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_diary_user_date ON diary_entries(user_id, entry_date);

CREATE TABLE water_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    entry_date  DATE NOT NULL,
    ml          INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_water_user_date ON water_entries(user_id, entry_date);
