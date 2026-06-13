-- Backfills proper fiber values on the V2 public seed (V3 added the column
-- with default 0) and adds a much bigger public library of common foods so
-- users have something to search for when adding diary entries or building
-- recipes. NEW ACCOUNTS still see an empty "Recipes" and "Ingredients" tab -
-- the public library only surfaces when the desktop client asks scope=all.

-- ---------------- 1. Fix fiber on existing V2 public ingredients ----------------

UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Egg';
UPDATE ingredients SET fiber_per_100g = 2.7  WHERE owner_user_id IS NULL AND name = 'White bread';
UPDATE ingredients SET fiber_per_100g = 6    WHERE owner_user_id IS NULL AND name = 'Whole wheat bread';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Butter';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Olive oil';
UPDATE ingredients SET fiber_per_100g = 0.4  WHERE owner_user_id IS NULL AND name = 'White rice, cooked';
UPDATE ingredients SET fiber_per_100g = 1.8  WHERE owner_user_id IS NULL AND name = 'Brown rice, cooked';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Chicken breast, cooked';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Salmon, cooked';
UPDATE ingredients SET fiber_per_100g = 2.6  WHERE owner_user_id IS NULL AND name = 'Banana';
UPDATE ingredients SET fiber_per_100g = 2.4  WHERE owner_user_id IS NULL AND name = 'Apple';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Whole milk';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Skim milk';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Greek yogurt';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Cheddar cheese';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Mozzarella';
UPDATE ingredients SET fiber_per_100g = 10   WHERE owner_user_id IS NULL AND name = 'Rolled oats, dry';
UPDATE ingredients SET fiber_per_100g = 6    WHERE owner_user_id IS NULL AND name = 'Peanut butter';
UPDATE ingredients SET fiber_per_100g = 6.7  WHERE owner_user_id IS NULL AND name = 'Avocado';
UPDATE ingredients SET fiber_per_100g = 1.2  WHERE owner_user_id IS NULL AND name = 'Tomato';
UPDATE ingredients SET fiber_per_100g = 0.5  WHERE owner_user_id IS NULL AND name = 'Cucumber';
UPDATE ingredients SET fiber_per_100g = 2.2  WHERE owner_user_id IS NULL AND name = 'Spinach';
UPDATE ingredients SET fiber_per_100g = 2.6  WHERE owner_user_id IS NULL AND name = 'Broccoli';
UPDATE ingredients SET fiber_per_100g = 1.8  WHERE owner_user_id IS NULL AND name = 'Potato, boiled';
UPDATE ingredients SET fiber_per_100g = 3    WHERE owner_user_id IS NULL AND name = 'Sweet potato, baked';
UPDATE ingredients SET fiber_per_100g = 1.8  WHERE owner_user_id IS NULL AND name = 'Pasta, cooked';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Beef, ground, cooked';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Tuna, canned in water';
UPDATE ingredients SET fiber_per_100g = 0.3  WHERE owner_user_id IS NULL AND name = 'Tofu';
UPDATE ingredients SET fiber_per_100g = 12.5 WHERE owner_user_id IS NULL AND name = 'Almonds';
UPDATE ingredients SET fiber_per_100g = 0.2  WHERE owner_user_id IS NULL AND name = 'Honey';
UPDATE ingredients SET fiber_per_100g = 11   WHERE owner_user_id IS NULL AND name = 'Dark chocolate';
UPDATE ingredients SET fiber_per_100g = 0    WHERE owner_user_id IS NULL AND name = 'Coffee, black';
UPDATE ingredients SET fiber_per_100g = 2.4  WHERE owner_user_id IS NULL AND name = 'Orange';

-- ---------------- 2. Bulk-add common public ingredients ----------------
-- Standard reference values per 100 g of edible portion (USDA-style).

INSERT INTO ingredients (name, kcal_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, fiber_per_100g, owner_user_id) VALUES
  -- Fruits
  ('Strawberry',                32,  0.7,  7.7,  0.3,  2,    NULL),
  ('Blueberry',                 57,  0.7,  14,   0.3,  2.4,  NULL),
  ('Raspberry',                 52,  1.2,  12,   0.7,  6.5,  NULL),
  ('Blackberry',                43,  1.4,  10,   0.5,  5.3,  NULL),
  ('Grape',                     67,  0.6,  17,   0.4,  0.9,  NULL),
  ('Mango',                     60,  0.8,  15,   0.4,  1.6,  NULL),
  ('Pineapple',                 50,  0.5,  13,   0.1,  1.4,  NULL),
  ('Watermelon',                30,  0.6,  8,    0.2,  0.4,  NULL),
  ('Peach',                     39,  0.9,  10,   0.3,  1.5,  NULL),
  ('Pear',                      57,  0.4,  15,   0.1,  3.1,  NULL),
  ('Plum',                      46,  0.7,  11,   0.3,  1.4,  NULL),
  ('Cherry',                    50,  1,    12,   0.3,  1.6,  NULL),
  ('Kiwi',                      61,  1.1,  15,   0.5,  3,    NULL),
  ('Pomegranate',               83,  1.7,  19,   1.2,  4,    NULL),
  ('Lemon',                     29,  1.1,  9,    0.3,  2.8,  NULL),
  ('Grapefruit',                42,  0.8,  11,   0.1,  1.6,  NULL),
  ('Papaya',                    43,  0.5,  11,   0.3,  1.7,  NULL),
  ('Apricot',                   48,  1.4,  11,   0.4,  2,    NULL),
  ('Cantaloupe',                34,  0.8,  8,    0.2,  0.9,  NULL),
  ('Fig',                       74,  0.8,  19,   0.3,  2.9,  NULL),
  ('Date',                      282, 2.5,  75,   0.4,  8,    NULL),
  -- Vegetables
  ('Lettuce',                   15,  1.4,  2.9,  0.2,  1.3,  NULL),
  ('Kale',                      49,  4.3,  9,    0.9,  3.6,  NULL),
  ('Cauliflower',               25,  1.9,  5,    0.3,  2,    NULL),
  ('Carrot',                    41,  0.9,  10,   0.2,  2.8,  NULL),
  ('Onion',                     40,  1.1,  9,    0.1,  1.7,  NULL),
  ('Garlic',                    149, 6.4,  33,   0.5,  2.1,  NULL),
  ('Bell pepper, red',          31,  1,    6,    0.3,  2.1,  NULL),
  ('Bell pepper, green',        20,  0.9,  4.6,  0.2,  1.7,  NULL),
  ('Zucchini',                  17,  1.2,  3.1,  0.3,  1,    NULL),
  ('Eggplant',                  25,  1,    6,    0.2,  3,    NULL),
  ('Mushroom, white',           22,  3.1,  3.3,  0.3,  1,    NULL),
  ('Asparagus',                 20,  2.2,  3.9,  0.1,  2.1,  NULL),
  ('Beet',                      43,  1.6,  10,   0.2,  2.8,  NULL),
  ('Brussels sprouts',          43,  3.4,  9,    0.3,  3.8,  NULL),
  ('Celery',                    16,  0.7,  3,    0.2,  1.6,  NULL),
  ('Cabbage',                   25,  1.3,  6,    0.1,  2.5,  NULL),
  ('Corn',                      86,  3.2,  19,   1.2,  2.7,  NULL),
  ('Green peas',                81,  5.4,  14,   0.4,  5.7,  NULL),
  ('Arugula',                   25,  2.6,  3.6,  0.7,  1.6,  NULL),
  -- Grains and starches
  ('Quinoa, cooked',            120, 4.4,  21,   1.9,  2.8,  NULL),
  ('Couscous, cooked',          112, 3.8,  23,   0.2,  1.4,  NULL),
  ('Barley, cooked',            123, 2.3,  28,   0.4,  3.8,  NULL),
  ('Bulgur, cooked',            83,  3.1,  19,   0.2,  4.5,  NULL),
  ('Rye bread',                 259, 8.5,  48,   3.3,  6,    NULL),
  ('Sourdough bread',           230, 9,    47,   1,    2.4,  NULL),
  ('Bagel, plain',              257, 10,   51,   1.5,  2.2,  NULL),
  ('Tortilla, flour',           304, 8.7,  50,   7.9,  3,    NULL),
  ('Tortilla, corn',            218, 5.7,  45,   2.9,  6.3,  NULL),
  ('Rice noodles, cooked',      109, 0.9,  25,   0.2,  1,    NULL),
  ('Pancake',                   227, 6.4,  28,   9.7,  1,    NULL),
  -- Proteins
  ('Chicken thigh, cooked',     209, 26,   0,    11,   0,    NULL),
  ('Beef steak, cooked',        271, 26,   0,    18,   0,    NULL),
  ('Pork loin, cooked',         173, 26,   0,    7,    0,    NULL),
  ('Bacon, cooked',             541, 37,   1.4,  42,   0,    NULL),
  ('Sausage, cooked',           296, 18,   1,    24,   0,    NULL),
  ('Ham',                       145, 21,   1.5,  6,    0,    NULL),
  ('Turkey breast, cooked',     135, 30,   0,    1,    0,    NULL),
  ('Cod, cooked',               105, 23,   0,    0.9,  0,    NULL),
  ('Tilapia, cooked',           129, 26,   0,    2.7,  0,    NULL),
  ('Sardines, canned in oil',   208, 25,   0,    11,   0,    NULL),
  ('Shrimp, cooked',            99,  24,   0.2,  0.3,  0,    NULL),
  ('Lentils, cooked',           116, 9,    20,   0.4,  7.9,  NULL),
  ('Black beans, cooked',       132, 8.9,  24,   0.5,  8.7,  NULL),
  ('Kidney beans, cooked',      127, 8.7,  23,   0.5,  6.4,  NULL),
  ('Chickpeas, cooked',         164, 8.9,  27,   2.6,  7.6,  NULL),
  ('White beans, cooked',       139, 9.7,  25,   0.4,  6.3,  NULL),
  ('Edamame, cooked',           121, 12,   9,    5,    5,    NULL),
  -- Dairy
  ('Plain yogurt',              59,  3.5,  4.7,  3.3,  0,    NULL),
  ('Cottage cheese',            98,  11,   3.4,  4.3,  0,    NULL),
  ('Feta cheese',               264, 14,   4.1,  21,   0,    NULL),
  ('Parmesan',                  431, 38,   4.1,  29,   0,    NULL),
  ('Ricotta',                   174, 11,   3,    13,   0,    NULL),
  ('Cream cheese',              342, 6,    5.5,  34,   0,    NULL),
  ('Heavy cream',               340, 2.8,  2.8,  36,   0,    NULL),
  ('Sour cream',                198, 2.4,  4.6,  19,   0,    NULL),
  ('Ice cream, vanilla',        207, 3.5,  24,   11,   0.7,  NULL),
  -- Nuts and seeds
  ('Walnut',                    654, 15,   14,   65,   6.7,  NULL),
  ('Cashew',                    553, 18,   30,   44,   3.3,  NULL),
  ('Pistachio',                 560, 20,   28,   45,   10,   NULL),
  ('Pecan',                     691, 9.2,  14,   72,   9.6,  NULL),
  ('Hazelnut',                  628, 15,   17,   61,   9.7,  NULL),
  ('Sunflower seed',            584, 21,   20,   51,   8.6,  NULL),
  ('Pumpkin seed',              559, 30,   11,   49,   6,    NULL),
  ('Chia seed',                 486, 17,   42,   31,   34,   NULL),
  ('Flax seed',                 534, 18,   29,   42,   27,   NULL),
  -- Oils and fats
  ('Coconut oil',               862, 0,    0,    100,  0,    NULL),
  ('Canola oil',                884, 0,    0,    100,  0,    NULL),
  ('Sunflower oil',             884, 0,    0,    100,  0,    NULL),
  ('Sesame oil',                884, 0,    0,    100,  0,    NULL),
  -- Sweets, snacks, sauces
  ('Milk chocolate',            535, 7.7,  59,   30,   3.4,  NULL),
  ('Cookies, chocolate chip',   488, 5,    65,   23,   2,    NULL),
  ('Brownie',                   466, 6,    56,   27,   3,    NULL),
  ('Pretzel',                   384, 10,   80,   3,    3,    NULL),
  ('Popcorn, air-popped',       387, 13,   78,   4.5,  14,   NULL),
  ('Tortilla chips',            503, 7,    64,   26,   4.5,  NULL),
  ('Granola bar',               471, 10,   64,   20,   4.5,  NULL),
  ('Maple syrup',               260, 0,    67,   0.2,  0,    NULL),
  ('Sugar',                     387, 0,    100,  0,    0,    NULL),
  ('Jam',                       278, 0.4,  68,   0.1,  1,    NULL),
  ('Ketchup',                   112, 1.7,  27,   0.1,  0.3,  NULL),
  ('Mayonnaise',                680, 1,    0.6,  75,   0,    NULL),
  ('Mustard',                   66,  4.4,  5,    4,    3.3,  NULL),
  ('Soy sauce',                 53,  8,    5,    0.1,  0.8,  NULL),
  ('Hummus',                    166, 8,    14,   10,   6,    NULL),
  ('Guacamole',                 230, 2,    8,    21,   6.7,  NULL),
  -- Beverages
  ('Orange juice',              45,  0.7,  10,   0.2,  0.2,  NULL),
  ('Apple juice',               46,  0.1,  11,   0.1,  0.2,  NULL),
  ('Coca-Cola',                 42,  0,    11,   0,    0,    NULL),
  ('Beer',                      43,  0.5,  3.6,  0,    0,    NULL),
  ('Wine, red',                 85,  0.1,  2.6,  0,    0,    NULL),
  ('Tea, plain',                1,   0,    0.3,  0,    0,    NULL),
  ('Almond milk',               17,  0.6,  0.6,  1.5,  0.3,  NULL),
  ('Soy milk',                  33,  3.3,  1.7,  1.8,  0.6,  NULL),
  ('Oat milk',                  47,  1,    7,    1.5,  0.8,  NULL),
  ('Coconut milk',              230, 2.3,  6,    24,   2.2,  NULL);

-- ---------------- 3. Recompute fiber totals for public recipes -----------------

UPDATE recipes r SET total_fiber = sub.fiber
FROM (
    SELECT ri.recipe_id, SUM(i.fiber_per_100g * ri.amount_grams / 100.0) AS fiber
    FROM recipe_ingredients ri
    JOIN ingredients i ON i.id = ri.ingredient_id
    GROUP BY ri.recipe_id
) sub
WHERE r.id = sub.recipe_id AND r.owner_user_id IS NULL;
