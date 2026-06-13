-- Public ingredients (owner_user_id NULL = visible to everyone).
-- Values are per 100 g of edible portion (standard USDA-style references).

INSERT INTO ingredients (name, kcal_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, owner_user_id) VALUES
  ('Egg',                        155, 13,   1.1,  11,   NULL),
  ('White bread',                265, 9,    49,   3.2,  NULL),
  ('Whole wheat bread',          247, 13,   41,   3.4,  NULL),
  ('Butter',                     717, 0.9,  0.1,  81,   NULL),
  ('Olive oil',                  884, 0,    0,    100,  NULL),
  ('White rice, cooked',         130, 2.7,  28,   0.3,  NULL),
  ('Brown rice, cooked',         111, 2.6,  23,   0.9,  NULL),
  ('Chicken breast, cooked',     165, 31,   0,    3.6,  NULL),
  ('Salmon, cooked',             208, 20,   0,    13,   NULL),
  ('Banana',                     89,  1.1,  23,   0.3,  NULL),
  ('Apple',                      52,  0.3,  14,   0.2,  NULL),
  ('Whole milk',                 61,  3.2,  4.8,  3.3,  NULL),
  ('Skim milk',                  34,  3.4,  5,    0.1,  NULL),
  ('Greek yogurt',               59,  10,   3.6,  0.4,  NULL),
  ('Cheddar cheese',             402, 25,   1.3,  33,   NULL),
  ('Mozzarella',                 280, 28,   3.1,  17,   NULL),
  ('Rolled oats, dry',           379, 13,   68,   7,    NULL),
  ('Peanut butter',              588, 25,   20,   50,   NULL),
  ('Avocado',                    160, 2,    9,    15,   NULL),
  ('Tomato',                     18,  0.9,  3.9,  0.2,  NULL),
  ('Cucumber',                   15,  0.7,  3.6,  0.1,  NULL),
  ('Spinach',                    23,  2.9,  3.6,  0.4,  NULL),
  ('Broccoli',                   34,  2.8,  7,    0.4,  NULL),
  ('Potato, boiled',             87,  1.9,  20,   0.1,  NULL),
  ('Sweet potato, baked',        90,  2,    21,   0.1,  NULL),
  ('Pasta, cooked',              131, 5,    25,   1.1,  NULL),
  ('Beef, ground, cooked',       254, 26,   0,    17,   NULL),
  ('Tuna, canned in water',      116, 26,   0,    1,    NULL),
  ('Tofu',                       76,  8,    1.9,  4.8,  NULL),
  ('Almonds',                    579, 21,   22,   50,   NULL),
  ('Honey',                      304, 0.3,  82,   0,    NULL),
  ('Dark chocolate',             598, 7.8,  46,   43,   NULL),
  ('Coffee, black',              1,   0.1,  0,    0,    NULL),
  ('Orange',                     47,  0.9,  12,   0.1,  NULL);

-- Two public recipes built from the above ingredients.
INSERT INTO recipes (name, servings, total_kcal, total_protein, total_carbs, total_fat, owner_user_id) VALUES
  ('Avocado toast',                     1, 0, 0, 0, 0, NULL),
  ('Greek yogurt with banana and oats', 1, 0, 0, 0, 0, NULL);

INSERT INTO recipe_ingredients (recipe_id, ingredient_id, amount_grams) VALUES
  ((SELECT id FROM recipes WHERE name='Avocado toast'), (SELECT id FROM ingredients WHERE name='Whole wheat bread' AND owner_user_id IS NULL), 60),
  ((SELECT id FROM recipes WHERE name='Avocado toast'), (SELECT id FROM ingredients WHERE name='Avocado'           AND owner_user_id IS NULL), 80),
  ((SELECT id FROM recipes WHERE name='Avocado toast'), (SELECT id FROM ingredients WHERE name='Olive oil'         AND owner_user_id IS NULL), 5),
  ((SELECT id FROM recipes WHERE name='Greek yogurt with banana and oats'), (SELECT id FROM ingredients WHERE name='Greek yogurt'    AND owner_user_id IS NULL), 200),
  ((SELECT id FROM recipes WHERE name='Greek yogurt with banana and oats'), (SELECT id FROM ingredients WHERE name='Banana'          AND owner_user_id IS NULL), 120),
  ((SELECT id FROM recipes WHERE name='Greek yogurt with banana and oats'), (SELECT id FROM ingredients WHERE name='Rolled oats, dry' AND owner_user_id IS NULL), 40);

-- Compute totals from the line items so they stay consistent.
UPDATE recipes r SET
    total_kcal    = sub.kcal,
    total_protein = sub.protein,
    total_carbs   = sub.carbs,
    total_fat     = sub.fat
FROM (
    SELECT ri.recipe_id,
           CAST(ROUND(SUM(i.kcal_per_100g    * ri.amount_grams / 100.0)) AS INT) AS kcal,
           SUM(i.protein_per_100g * ri.amount_grams / 100.0) AS protein,
           SUM(i.carbs_per_100g   * ri.amount_grams / 100.0) AS carbs,
           SUM(i.fat_per_100g     * ri.amount_grams / 100.0) AS fat
    FROM recipe_ingredients ri
    JOIN ingredients i ON i.id = ri.ingredient_id
    GROUP BY ri.recipe_id
) sub
WHERE r.id = sub.recipe_id;
