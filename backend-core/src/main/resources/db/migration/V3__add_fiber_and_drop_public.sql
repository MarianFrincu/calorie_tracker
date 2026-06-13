-- Adds fiber tracking to the nutrition model.
-- Public ingredients/recipes from V2 stay in the DB but are no longer surfaced
-- by the search queries (changed in IngredientRepository / RecipeRepository),
-- so new accounts effectively start with an empty library.

ALTER TABLE ingredients   ADD COLUMN fiber_per_100g DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE recipes       ADD COLUMN total_fiber    DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE diary_entries ADD COLUMN fiber          DOUBLE PRECISION NOT NULL DEFAULT 0;
