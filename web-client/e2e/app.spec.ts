import { expect, test, type Locator, type Page } from '@playwright/test';

/**
 * End-to-end tests of every area of the web app, through a real browser, the
 * real nginx, gateway, services and database.
 *
 * Works against the local `dev` stack (no sign-in) and against a deployed
 * Cognito stack when E2E_EMAIL / E2E_PASSWORD name a confirmed user. Every
 * test fails on any browser console error or failed /api request.
 *
 * The account-deletion test only runs with E2E_ALLOW_DELETE=1, because it
 * wipes the account's data (CI sets it; `make e2e` on your machine doesn't).
 */

// Unique per run so reruns against the same database never collide.
const TAG = `${Date.now() % 1_000_000}`;

test.describe.configure({ mode: 'serial' });

let problems: string[] = [];

test.beforeEach(async ({ page }) => {
  problems = [];
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`console: ${m.text()}`);
  });
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
  page.on('response', (r) => {
    // 429 = the per-user AI quota doing its job; not an app failure.
    if (r.url().includes('/api/') && r.status() >= 400 && r.status() !== 429) {
      problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`);
    }
  });
});

test.afterEach(() => {
  expect(problems, 'browser errors or failed API calls').toEqual([]);
});

// ---------------------------------------------------------------- helpers

/** Signs in if needed, completes onboarding if needed, and lands on the app. */
async function openApp(page: Page) {
  await page.goto('/day');
  const signIn = page.getByRole('button', { name: 'Continue' });
  const shell = page.getByRole('navigation', { name: 'Main' });
  await expect(signIn.or(shell)).toBeVisible();
  if (await signIn.isVisible()) {
    const email = process.env.E2E_EMAIL;
    const password = process.env.E2E_PASSWORD;
    test.skip(!email || !password, 'Sign-in required: set E2E_EMAIL and E2E_PASSWORD');
    await page.getByLabel('Email').fill(email!);
    await page.getByLabel('Password', { exact: true }).fill(password!);
    await signIn.click();
    await expect(shell).toBeVisible();
  }
  const onboarding = page.getByText('Welcome! Fill in your sex');
  await expect(onboarding.or(page.getByText('kcal remaining'))).toBeVisible();
  if (await onboarding.isVisible()) {
    await page.getByLabel('Sex').selectOption('MALE');
    await page.getByLabel('Age').fill('30');
    await page.getByLabel('Height (cm)').fill('180');
    await page.getByLabel('Weight (kg)').fill('80');
    await page.getByLabel('Activity level').selectOption('MODERATE');
    await page.getByRole('button', { name: 'Save profile' }).click();
    await expect(page.getByText(/Saved\. BMR/)).toBeVisible();
  }
}

/** On a phone the sidebar is a rail: open the drawer before navigating. */
async function go(page: Page, label: string) {
  const link = page.getByRole('link', { name: label, exact: true });
  if (!(await link.isVisible())) await page.getByRole('button', { name: 'Expand sidebar' }).click();
  await link.click();
}

function dialog(page: Page): Locator {
  return page.getByRole('dialog');
}

async function confirm(page: Page) {
  await dialog(page).locator('.modal-footer button').last().click();
  await expect(dialog(page)).toBeHidden();
}

async function mealKcal(page: Page, meal: string): Promise<number> {
  const text = await page.getByText(new RegExp(`${meal}\\s*—\\s*\\d+ kcal`)).first().innerText();
  return Number(/(\d+) kcal/.exec(text)![1]);
}

/** A table cell whose text starts with `text` (own foods render as "Name  (mine)"). */
function cell(scope: Page | Locator, text: string): Locator {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return scope.getByRole('cell').filter({ hasText: new RegExp(`^${escaped}(\\s+\\(mine\\))?$`) }).first();
}

async function search(scope: Page | Locator, placeholder: string, query: string) {
  const box = scope.getByPlaceholder(placeholder);
  await box.fill(query);
  await box.press('Enter');
}

// ---------------------------------------------------------------- tests

test('day view: add a food, water, move, copy and delete entries', async ({ page }) => {
  await openApp(page);
  await go(page, 'Day');
  const grams = String(101 + (Date.now() % 97)); // unique amount to find our row again

  await page.getByRole('button', { name: '+ Add to Breakfast' }).click();
  await search(dialog(page), 'search foods (press Enter)', 'egg');
  await cell(dialog(page), 'Egg').click();
  await dialog(page).getByLabel('Amount (g):').fill(grams);
  await confirm(page);
  const row = page.locator('.entry-row', { hasText: `${grams} g` });
  await expect(row).toHaveCount(1);

  const chips = page.locator('.water-chip');
  const before = await chips.count();
  await page.getByRole('button', { name: '+250 ml' }).click();
  await expect(chips).toHaveCount(before + 1);
  await chips.last().click();
  await expect(chips).toHaveCount(before);

  const dinner = await mealKcal(page, 'Dinner');
  await row.getByRole('button', { name: /Actions for/ }).click();
  await page.getByRole('menuitem', { name: /Move to other/ }).click();
  await dialog(page).getByLabel('Meal').selectOption('DINNER');
  await confirm(page);
  await expect.poll(() => mealKcal(page, 'Dinner')).toBeGreaterThan(dinner);

  const snack = await mealKcal(page, 'Snack');
  await row.getByRole('button', { name: /Actions for/ }).click();
  await page.getByRole('menuitem', { name: /Copy to other/ }).click();
  await dialog(page).getByLabel('Meal').selectOption('SNACK');
  await confirm(page);
  await expect.poll(() => mealKcal(page, 'Snack')).toBeGreaterThan(snack);
  await expect(row).toHaveCount(2);

  for (let i = 0; i < 2; i++) {
    await row.first().getByRole('button', { name: /Actions for/ }).click();
    await page.getByRole('menuitem', { name: 'Delete' }).click();
    await expect(row).toHaveCount(1 - i);
  }

  // Day navigation
  await page.getByRole('button', { name: '‹ Prev' }).click();
  await page.getByRole('button', { name: 'Today' }).click();
  await expect(page.getByText('kcal remaining')).toBeVisible();
});

test('library: create a food, build a recipe with it, log the recipe, then delete both', async ({ page }) => {
  await openApp(page);
  const food = `E2E granola ${TAG}`;
  const recipe = `E2E bowl ${TAG}`;

  await go(page, 'Recipes');
  await page.getByRole('tab', { name: 'Foods' }).click();
  await page.getByRole('button', { name: '+ New food' }).click();
  await dialog(page).getByLabel('Name').fill(food);
  await dialog(page).getByLabel('kcal per 100 g').fill('450');
  await dialog(page).getByLabel('Protein (g)').fill('10');
  await dialog(page).getByLabel('Carbs (g)').fill('60');
  await dialog(page).getByLabel('Fat (g)').fill('18');
  await dialog(page).getByLabel('Fiber (g)').fill('7');
  await confirm(page);
  await search(page, 'search your foods (press Enter)', food);
  await expect(cell(page, food)).toBeVisible();

  await page.getByRole('tab', { name: 'Recipes' }).click();
  await page.getByRole('button', { name: '+ New recipe' }).click();
  await dialog(page).getByLabel('Name', { exact: true }).fill(recipe);
  for (const [query, pick, grams] of [[food, food, '80'], ['egg', 'Egg', '100']]) {
    await search(dialog(page), 'search the library (own + public)', query);
    await cell(dialog(page), pick).click();
    await dialog(page).getByLabel('Amount (g):').fill(grams);
    await dialog(page).getByRole('button', { name: 'Add to recipe' }).click();
  }
  await expect(dialog(page).getByText(/Raw totals: 515 kcal/)).toBeVisible(); // 360 + 155
  await confirm(page);
  await search(page, 'search your recipes (press Enter)', recipe);
  await expect(cell(page, recipe)).toBeVisible();

  await go(page, 'Day');
  await page.getByRole('button', { name: '+ Add to Lunch' }).click();
  await dialog(page).getByRole('tab', { name: 'Recipe' }).click();
  await search(dialog(page), 'search recipes (press Enter)', recipe);
  await cell(dialog(page), recipe).click();
  await dialog(page).getByLabel('Amount (g, cooked):').fill('90');
  await confirm(page);
  const logged = page.locator('.entry-row', { hasText: recipe });
  await expect(logged).toBeVisible();
  await logged.getByRole('button', { name: /Actions for/ }).click();
  await page.getByRole('menuitem', { name: 'Delete' }).click();
  await expect(logged).toHaveCount(0);

  await go(page, 'Recipes');
  await search(page, 'search your recipes (press Enter)', recipe);
  await cell(page, recipe).click();
  await page.getByRole('button', { name: 'Delete selected' }).click();
  await expect(cell(page, recipe)).toHaveCount(0);
  await page.getByRole('tab', { name: 'Foods' }).click();
  await search(page, 'search your foods (press Enter)', food);
  await cell(page, food).click();
  await page.getByRole('button', { name: 'Delete selected' }).click();
  await expect(cell(page, food)).toHaveCount(0);
});

test('AI: parse a meal into the diary and save a recipe blueprint', async ({ page }) => {
  await openApp(page);
  await go(page, 'AI');
  await page.getByLabel('Describe your meal').fill('2 eggs and a banana');
  await page.getByRole('button', { name: 'Send' }).click();
  await expect(page.getByText('Parsed for diary').last()).toBeVisible();
  await page.getByRole('button', { name: 'Add all to diary' }).last().click();
  await expect(page.getByRole('button', { name: 'Added!' }).last()).toBeVisible();

  const name = `E2E AI dish ${TAG}`;
  await page.getByLabel('Mode').selectOption('RECIPE');
  await page.getByLabel('Describe your meal').fill('chicken 200g and rice 150g');
  await page.getByRole('button', { name: 'Send' }).click();
  await page.getByLabel(/^Name/).last().fill(name);
  await page.getByRole('button', { name: 'Save as recipe' }).last().click();
  await expect(page.getByRole('button', { name: `Saved as "${name}"` })).toBeVisible();

  await go(page, 'Recipes');
  await search(page, 'search your recipes (press Enter)', name);
  await cell(page, name).click();
  await page.getByRole('button', { name: 'Delete selected' }).click();
  await expect(cell(page, name)).toHaveCount(0);
});

test('explore: compare foods and run an advanced search', async ({ page }) => {
  await openApp(page);
  await go(page, 'Explore');
  for (const food of ['Egg', 'Banana']) {
    await search(page, 'search (press Enter)', food.toLowerCase());
    await cell(page, `${food} (per 100 g)`).click();
    await page.getByRole('button', { name: 'Add to comparison' }).click();
  }
  await expect(page.getByText('Add at least 2 foods')).toHaveCount(0);
  await expect(page.locator('.recharts-surface').first()).toBeVisible();

  await page.getByRole('tab', { name: 'Advanced search' }).click();
  await page.getByRole('button', { name: 'Apply', exact: true }).click();
  await expect(page.getByText('Apply at least one rule')).toHaveCount(0);
});

test('objective, weight and reports', async ({ page }) => {
  await openApp(page);

  // Number fields: clearing leaves them empty (no stray 0), leading zeros go.
  await go(page, 'Profile');
  const age = page.getByLabel('Age');
  await age.fill('');
  await expect(age).toHaveValue('');
  await age.pressSequentially('031');
  await expect(age).toHaveValue('31');
  await page.getByLabel('Weight (kg)').fill('80,5');
  await expect(page.getByLabel('Weight (kg)')).toHaveValue('80.5');

  await go(page, 'Objective');
  await page.getByRole('button', { name: /Change objective/ }).click();
  await dialog(page).getByLabel('Goal').selectOption('LOSE');
  await dialog(page).getByLabel('Intensity').selectOption('20');
  await confirm(page);
  await expect(page.getByText(/Lose/).first()).toBeVisible();

  await go(page, 'Weight');
  await expect(page.getByRole('button', { name: 'Save weight' })).toBeEnabled();
  const kg = (70 + (Date.now() % 100) / 10).toFixed(1);
  await page.getByLabel('Weight (kg):').fill(kg);
  await page.getByRole('button', { name: 'Save weight' }).click();
  await expect(page.getByText(kg).first()).toBeVisible();

  await go(page, 'Reports');
  await expect(page.locator('.recharts-surface').first()).toBeVisible();
  for (const metric of ['Protein', 'Water']) {
    await page.getByLabel('Metric:').selectOption({ label: metric });
    await expect(page.locator('.recharts-surface').first()).toBeVisible();
  }
  await page.getByLabel('Period:').selectOption({ index: 1 });
  await expect(page.locator('.recharts-surface').first()).toBeVisible();
});

test('account deletion wipes the account', async ({ page }) => {
  test.skip(process.env.E2E_ALLOW_DELETE !== '1', 'destructive: set E2E_ALLOW_DELETE=1');
  await openApp(page);
  await go(page, 'Profile');
  await page.getByRole('button', { name: /Delete my account/ }).click();
  const ok = dialog(page).getByRole('button', { name: 'Delete forever' });
  await expect(ok).toBeDisabled();
  await dialog(page).getByLabel('Type DELETE to confirm').fill('DELETE');
  await ok.click();
  // dev stack: back to a blank profile; Cognito stack: back to the sign-in screen.
  await expect(page.getByText('Welcome! Fill in your sex').or(page.getByRole('button', { name: 'Continue' })))
    .toBeVisible();
});
