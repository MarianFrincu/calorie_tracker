/**
 * Build or edit a recipe. The ingredient search hits the full library (own +
 * public, 20 at a time) so custom ingredients mix with the public seed set.
 * "+ New food" creates one inline. Port of RecipeBuilderDialog.java.
 */

import { useEffect, useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { api } from '../api/client';
import type { CreateIngredientRequest, CreateRecipeRequest, Ingredient, Recipe } from '../api/types';
import { DataTable, LoadMoreBar, SearchBar, type Column } from '../components/DataTable';
import { Modal } from '../components/Modal';
import { useToast } from '../components/Toast';
import { usePager } from '../components/usePager';
import { fmt, parseNumberOr } from '../lib/format';
import { IngredientEditorDialog } from './IngredientEditorDialog';

interface LineRow {
  ingredient: Ingredient;
  amountGrams: number;
}

const searchColumns: Column<Ingredient>[] = [
  {
    key: 'name',
    header: 'Ingredient',
    render: (i) => (
      <>
        {i.name}
        {i.isPublic ? '' : '  (mine)'}
      </>
    ),
  },
  { key: 'kcal', header: 'kcal/100g', numeric: true, render: (i) => i.kcalPer100g },
  { key: 'fiber', header: 'Fiber/100g', numeric: true, render: (i) => `${fmt(i.fiberPer100g)} g` },
];

interface Props {
  editing?: Recipe | null;
  busy?: boolean;
  onSubmit: (request: CreateRecipeRequest) => void;
  onCancel: () => void;
}

export function RecipeBuilderDialog({ editing = null, busy, onSubmit, onCancel }: Props) {
  const toast = useToast();
  const isEdit = editing != null;

  const [name, setName] = useState(editing?.name ?? '');
  const [lines, setLines] = useState<LineRow[]>([]);
  const [selected, setSelected] = useState<Ingredient | null>(null);
  const [amount, setAmount] = useState('100');
  const [cookedGrams, setCookedGrams] = useState('');
  // Once the user types a cooked weight we stop auto-syncing it to the raw sum.
  const [cookedTouched, setCookedTouched] = useState(isEdit);
  const [creatingIngredient, setCreatingIngredient] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pager = usePager<Ingredient>((q, page, size, signal) =>
    api.searchAllIngredients(q, page, size, signal),
  );

  // Pre-fill from the recipe being edited: each line's ingredient is fetched by
  // id so we get its full per-100 g macros for the running totals.
  useEffect(() => {
    if (!editing) return;
    if (editing.totalCookedGrams > 0) setCookedGrams(String(Math.round(editing.totalCookedGrams)));
    let cancelled = false;
    void (async () => {
      const loaded: LineRow[] = [];
      for (const line of editing.ingredients) {
        try {
          const ingredient = await api.getIngredient(line.ingredientId);
          loaded.push({ ingredient, amountGrams: line.amountGrams });
        } catch {
          // A line whose ingredient was deleted can't be rebuilt; skip it rather
          // than failing the whole dialog.
        }
      }
      if (!cancelled) setLines(loaded);
    })();
    return () => {
      cancelled = true;
    };
  }, [editing]);

  const rawSum = useMemo(() => lines.reduce((sum, l) => sum + l.amountGrams, 0), [lines]);

  // Keep the cooked-weight input mirroring the raw sum until the user edits it.
  useEffect(() => {
    if (cookedTouched) return;
    setCookedGrams(rawSum > 0 ? String(Math.round(rawSum)) : '');
  }, [rawSum, cookedTouched]);

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, l) => {
        const factor = l.amountGrams / 100;
        acc.kcal += l.ingredient.kcalPer100g * factor;
        acc.protein += l.ingredient.proteinPer100g * factor;
        acc.carbs += l.ingredient.carbsPer100g * factor;
        acc.fat += l.ingredient.fatPer100g * factor;
        acc.fiber += l.ingredient.fiberPer100g * factor;
        return acc;
      },
      { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0 },
    );
  }, [lines]);

  const cooked = parseNumberOr(cookedGrams, rawSum);
  const per100 = cooked > 0 ? 100 / cooked : 0;

  const createIngredient = useMutation({
    mutationFn: (request: CreateIngredientRequest) => api.createIngredient(request),
    onSuccess: (created) => {
      setCreatingIngredient(false);
      pager.refresh();
      toast.info(`"${created.name}" added to your library.`);
    },
    onError: toast.showError,
  });

  function addLine() {
    if (!selected) {
      setError('Pick a food first.');
      return;
    }
    const grams = Number(amount.trim().replace(',', '.'));
    if (!Number.isFinite(grams) || grams <= 0) {
      setError('Amount must be a positive number.');
      return;
    }
    setError(null);
    setLines((current) => [...current, { ingredient: selected, amountGrams: grams }]);
  }

  function submit() {
    if (!name.trim()) {
      setError('Name is required.');
      return;
    }
    if (lines.length === 0) {
      setError('Add at least one food to the recipe.');
      return;
    }
    setError(null);
    onSubmit({
      name: name.trim(),
      servings: 1,
      ingredients: lines.map((l) => ({
        ingredientId: l.ingredient.id,
        amountGrams: l.amountGrams,
      })),
      totalCookedGrams: cooked > 0 ? cooked : rawSum,
    });
  }

  const lineColumns: Column<LineRow>[] = [
    { key: 'food', header: 'Food', render: (l) => l.ingredient.name },
    { key: 'amount', header: 'Amount (g)', numeric: true, render: (l) => fmt(l.amountGrams) },
    {
      key: 'kcal',
      header: 'kcal',
      numeric: true,
      render: (l) => Math.round(l.ingredient.kcalPer100g * (l.amountGrams / 100)),
    },
    {
      key: 'remove',
      header: '',
      render: (l) => (
        <button
          type="button"
          className="btn btn-danger"
          aria-label={`Remove ${l.ingredient.name}`}
          onClick={() => setLines((current) => current.filter((row) => row !== l))}
        >
          ✕
        </button>
      ),
    },
  ];

  return (
    <>
      <Modal
        title={isEdit ? 'Edit recipe' : 'New recipe'}
        headerText={
          isEdit
            ? 'Edit ingredients and cooked weight. Public seed recipes are read-only — edit your own copy.'
            : 'Build a recipe by adding ingredients with their amount in grams.'
        }
        size="xwide"
        busy={busy}
        onOk={submit}
        onCancel={onCancel}
      >
        <div className="form-grid">
          <label htmlFor="r-name">Name</label>
          <input
            id="r-name"
            type="text"
            placeholder="e.g. Mediterranean salad"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setError(null);
            }}
          />

          <label htmlFor="r-cooked">Cooked weight (g)</label>
          <input
            id="r-cooked"
            type="number"
            min={0}
            step={1}
            placeholder="auto from sum of raw"
            value={cookedGrams}
            onChange={(e) => {
              setCookedTouched(true);
              setCookedGrams(e.target.value);
            }}
          />
        </div>

        <p className="muted">
          Override the cooked weight if cooking changes the dish weight (pasta absorbing water, meat
          losing it). Per-100 g values are based on the cooked weight.
        </p>

        <h3 className="section-title">Find foods</h3>
        <SearchBar pager={pager} placeholder="search the library (own + public)" />
        <DataTable
          columns={searchColumns}
          rows={pager.items}
          rowKey={(i) => i.id}
          selectedKey={selected?.id ?? null}
          onSelect={setSelected}
          onActivate={addLine}
          maxHeight={260}
          placeholder={
            pager.loading ? 'Loading…' : 'No foods match. Click "+ New food" to create one.'
          }
        />
        <LoadMoreBar pager={pager} />

        <div className="row wrap">
          <label className="muted nowrap" htmlFor="r-amount">
            Amount (g):
          </label>
          <input
            id="r-amount"
            type="number"
            min={1}
            step={1}
            style={{ width: 100 }}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <button type="button" className="btn btn-primary" onClick={addLine}>
            Add to recipe
          </button>
          <button type="button" className="btn" onClick={() => setCreatingIngredient(true)}>
            + New food
          </button>
        </div>

        <h3 className="section-title">Recipe ingredients</h3>
        <DataTable
          columns={lineColumns}
          rows={lines}
          rowKey={(l) => `${l.ingredient.id}-${l.amountGrams}-${lines.indexOf(l)}`}
          placeholder="No lines yet."
          maxHeight={220}
        />

        <p className="entry-name">
          Raw totals: {Math.round(totals.kcal)} kcal · P {totals.protein.toFixed(1)} g · C{' '}
          {totals.carbs.toFixed(1)} g · F {totals.fat.toFixed(1)} g · Fiber{' '}
          {totals.fiber.toFixed(1)} g (raw {Math.round(rawSum)} g, cooked {Math.round(cooked)} g)
        </p>
        <p className="entry-name">
          {cooked <= 0
            ? 'Per 100 g (cooked): —'
            : `Per 100 g (cooked): ${Math.round(totals.kcal * per100)} kcal · P ${(
                totals.protein * per100
              ).toFixed(1)} g · C ${(totals.carbs * per100).toFixed(1)} g · F ${(
                totals.fat * per100
              ).toFixed(1)} g · Fiber ${(totals.fiber * per100).toFixed(1)} g`}
        </p>

        {error ? <div className="inline-hint">{error}</div> : null}
      </Modal>

      {creatingIngredient ? (
        <IngredientEditorDialog
          busy={createIngredient.isPending}
          onCancel={() => setCreatingIngredient(false)}
          onSubmit={(request) => createIngredient.mutate(request)}
        />
      ) : null}
    </>
  );
}
