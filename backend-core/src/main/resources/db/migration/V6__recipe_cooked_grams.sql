-- Recipes get a cooked-weight field. The old `servings` model is replaced by
-- per-100g-cooked: a recipe stores the total raw kcal/macros (computed from
-- ingredients) and the user-entered cooked weight; the desktop client derives
-- per-100g values for display, and diary entries multiply per-100g × grams
-- of cooked food eaten.
--
-- Existing rows: keep their `servings` column for backward compatibility, but
-- the new `total_cooked_grams` defaults to 0; the service layer treats
-- 0 as "fall back to sum of raw ingredient grams" so old recipes still work
-- without a manual fix-up.

ALTER TABLE recipes
    ADD COLUMN total_cooked_grams DOUBLE PRECISION NOT NULL DEFAULT 0;
