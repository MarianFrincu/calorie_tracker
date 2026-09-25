/**
 * Modal "Add food" dialog: two tabs (Food / Recipe). Both search the full
 * library (own + public) 20 rows at a time. Returns an AddDiaryRequest ready
 * to POST to /api/diary. Port of AddFoodDialog.java.
 */

import { useState } from 'react';

import { api } from '../api/client';
import type { AddDiaryRequest, Ingredient, IsoDate, Meal, Recipe } from '../api/types';
import { DataTable, LoadMoreBar, SearchBar, type Column } from '../components/DataTable';
import { Modal } from '../components/Modal';
import { Tabs } from '../components/ui';
import { usePager } from '../components/usePager';
import { fmt, prettyMeal } from '../lib/format';
import { recipePer100g } from '../lib/nutrition';

type TabId = 'food' | 'recipe';

const TABS = [
  { id: 'food' as const, label: 'Food' },
  { id: 'recipe' as const, label: 'Recipe' },
];

const ingredientColumns: Column<Ingredient>[] = [
  {
    key: 'name',
    header: 'Name',
    render: (i) => (
      <>
        {i.name}
        {i.isPublic ? '' : '  (mine)'}
      </>
    ),
  },
  { key: 'kcal', header: 'kcal/100g', numeric: true, render: (i) => i.kcalPer100g },
  {
    key: 'macros',
    header: 'P / C / F / Fib (per 100g)',
    numeric: true,
    render: (i) =>
      `${i.proteinPer100g.toFixed(1)} / ${i.carbsPer100g.toFixed(1)} / ${i.fatPer100g.toFixed(1)} / ${i.fiberPer100g.toFixed(1)}`,
  },
];

const recipeColumns: Column<Recipe>[] = [
  {
    key: 'name',
    header: 'Name',
    render: (r) => (
      <>
        {r.name}
        {r.isPublic ? '' : '  (mine)'}
      </>
    ),
  },
  {
    key: 'kcal',
    header: 'kcal/100g',
    numeric: true,
    render: (r) => Math.round(recipePer100g.kcal(r)),
  },
  {
    key: 'macros',
    header: 'P / C / F / Fib (per 100g)',
    numeric: true,
    render: (r) =>
      `${recipePer100g.protein(r).toFixed(1)} / ${recipePer100g.carbs(r).toFixed(1)} / ${recipePer100g.fat(r).toFixed(1)} / ${recipePer100g.fiber(r).toFixed(1)}`,
  },
];

interface Props {
  date: IsoDate;
  meal: Meal;
  onSubmit: (request: AddDiaryRequest) => void;
  onCancel: () => void;
  busy?: boolean;
}

export function AddFoodDialog({ date, meal, onSubmit, onCancel, busy }: Props) {
  const [tab, setTab] = useState<TabId>('food');
  const [error, setError] = useState<string | null>(null);

  const [ingredient, setIngredient] = useState<Ingredient | null>(null);
  const [ingredientGrams, setIngredientGrams] = useState('100');

  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [recipeGrams, setRecipeGrams] = useState('100');

  const ingredientPager = usePager<Ingredient>((q, page, size, signal) =>
    api.searchAllIngredients(q, page, size, signal),
  );
  const recipePager = usePager<Recipe>((q, page, size, signal) =>
    api.searchAllRecipes(q, page, size, signal),
  );

  function submit() {
    setError(null);
    if (tab === 'food') {
      if (!ingredient) {
        setError('Pick a food from the list before clicking OK.');
        return;
      }
      const grams = Number(ingredientGrams.trim().replace(',', '.'));
      if (!Number.isFinite(grams) || grams <= 0) {
        setError('Enter the amount in grams.');
        return;
      }
      onSubmit({ date, meal, ingredientId: ingredient.id, amountGrams: grams });
    } else {
      if (!recipe) {
        setError('Pick a recipe from the list before clicking OK.');
        return;
      }
      const grams = Number(recipeGrams.trim().replace(',', '.'));
      if (!Number.isFinite(grams) || grams <= 0) {
        setError('Enter the amount in grams (cooked).');
        return;
      }
      // The backend prefers per-100g-cooked × grams eaten; `servings` is the
      // legacy path and is deliberately not sent.
      onSubmit({ date, meal, recipeId: recipe.id, amountGrams: grams });
    }
  }

  return (
    <Modal
      title={`Add to ${prettyMeal(meal)}`}
      headerText="Search the library (yours + public), then choose an amount."
      size="wide"
      onOk={submit}
      onCancel={onCancel}
      busy={busy}
    >
      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'food' ? (
        <>
          <SearchBar pager={ingredientPager} placeholder="search foods (press Enter)" />
          <DataTable
            columns={ingredientColumns}
            rows={ingredientPager.items}
            rowKey={(i) => i.id}
            selectedKey={ingredient?.id ?? null}
            onSelect={setIngredient}
            onActivate={submit}
            maxHeight={360}
            placeholder={
              ingredientPager.loading
                ? 'Loading…'
                : 'No foods yet. Try searching a common one, or add one in Recipes → Foods.'
            }
          />
          <LoadMoreBar pager={ingredientPager} />
          <div className="row">
            <label className="muted nowrap" htmlFor="add-grams">
              Amount (g):
            </label>
            <input
              id="add-grams"
              type="number"
              min={1}
              step={1}
              style={{ width: 120 }}
              value={ingredientGrams}
              onChange={(e) => setIngredientGrams(e.target.value)}
            />
            {ingredient ? (
              <span className="muted">
                {ingredient.name} · {Math.round(ingredient.kcalPer100g * (Number(ingredientGrams) || 0) / 100)} kcal
              </span>
            ) : null}
          </div>
        </>
      ) : (
        <>
          <SearchBar pager={recipePager} placeholder="search recipes (press Enter)" />
          <DataTable
            columns={recipeColumns}
            rows={recipePager.items}
            rowKey={(r) => r.id}
            selectedKey={recipe?.id ?? null}
            onSelect={setRecipe}
            onActivate={submit}
            maxHeight={360}
            placeholder={
              recipePager.loading
                ? 'Loading…'
                : 'No recipes match. Build one in the Recipes tab, or have the AI tab draft one.'
            }
          />
          <LoadMoreBar pager={recipePager} />
          <div className="row">
            <label className="muted nowrap" htmlFor="add-recipe-grams">
              Amount (g, cooked):
            </label>
            <input
              id="add-recipe-grams"
              type="number"
              min={1}
              step={1}
              style={{ width: 120 }}
              value={recipeGrams}
              onChange={(e) => setRecipeGrams(e.target.value)}
            />
            {recipe ? (
              <span className="muted">
                {recipe.name} ·{' '}
                {Math.round((recipePer100g.kcal(recipe) * (Number(recipeGrams) || 0)) / 100)} kcal
              </span>
            ) : null}
          </div>
          {recipe ? (
            <p className="muted">
              Per 100 g cooked: {fmt(recipePer100g.kcal(recipe))} kcal · P{' '}
              {fmt(recipePer100g.protein(recipe))} g · C {fmt(recipePer100g.carbs(recipe))} g · F{' '}
              {fmt(recipePer100g.fat(recipe))} g
            </p>
          ) : null}
        </>
      )}

      {error ? <div className="inline-hint">{error}</div> : null}
    </Modal>
  );
}
