/**
 * Library view with two tabs: Recipes and Foods. Both show ONLY the user's own
 * items — public rows surface in the pickers instead. Port of RecipesView.java.
 */

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { api } from '../api/client';
import type {
  CreateIngredientRequest,
  CreateRecipeRequest,
  Ingredient,
  Recipe,
} from '../api/types';
import { IngredientEditorDialog } from '../dialogs/IngredientEditorDialog';
import { RecipeBuilderDialog } from '../dialogs/RecipeBuilderDialog';
import { DataTable, LoadMoreBar, SearchBar, type Column } from '../components/DataTable';
import { useToast } from '../components/Toast';
import { Tabs } from '../components/ui';
import { usePager } from '../components/usePager';
import { fmt } from '../lib/format';
import { effectiveCookedGrams, recipePer100g } from '../lib/nutrition';

const TABS = [
  { id: 'recipes' as const, label: 'Recipes' },
  { id: 'foods' as const, label: 'Foods' },
];

type TabId = (typeof TABS)[number]['id'];

export function RecipesView() {
  const [tab, setTab] = useState<TabId>('recipes');
  return (
    <>
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      {tab === 'recipes' ? <RecipesTab /> : <FoodsTab />}
    </>
  );
}

// ---------------- Recipes ----------------

function RecipesTab() {
  const toast = useToast();
  const pager = usePager<Recipe>((q, page, size, signal) =>
    api.listMyRecipes(q, page, size, signal),
  );
  const [selected, setSelected] = useState<Recipe | null>(null);
  const [editorOpen, setEditorOpen] = useState<'new' | 'edit' | null>(null);

  const afterWrite = () => {
    setEditorOpen(null);
    setSelected(null);
    pager.refresh();
  };

  const create = useMutation({
    mutationFn: (body: CreateRecipeRequest) => api.createRecipe(body),
    onSuccess: afterWrite,
    onError: toast.showError,
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateRecipeRequest }) =>
      api.updateRecipe(id, body),
    onSuccess: afterWrite,
    onError: toast.showError,
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.deleteRecipe(id),
    onSuccess: afterWrite,
    onError: toast.showError,
  });

  const columns: Column<Recipe>[] = [
    { key: 'name', header: 'Name', render: (r) => r.name },
    {
      key: 'cooked',
      header: 'Cooked (g)',
      numeric: true,
      render: (r) => Math.round(effectiveCookedGrams(r)),
    },
    {
      key: 'kcal',
      header: 'kcal/100g',
      numeric: true,
      render: (r) => Math.round(recipePer100g.kcal(r)),
    },
    {
      key: 'p',
      header: 'P/100g',
      numeric: true,
      render: (r) => `${fmt(recipePer100g.protein(r))} g`,
    },
    { key: 'c', header: 'C/100g', numeric: true, render: (r) => `${fmt(recipePer100g.carbs(r))} g` },
    { key: 'f', header: 'F/100g', numeric: true, render: (r) => `${fmt(recipePer100g.fat(r))} g` },
    {
      key: 'fib',
      header: 'Fib/100g',
      numeric: true,
      render: (r) => `${fmt(recipePer100g.fiber(r))} g`,
    },
    {
      key: 'ingredients',
      header: 'Ingredients',
      render: (r) =>
        r.ingredients.length === 0
          ? '(none)'
          : r.ingredients.map((l) => `${l.ingredientName} ${fmt(l.amountGrams)} g`).join(', '),
    },
  ];

  function openEdit() {
    if (!selected) {
      toast.warn('Pick a recipe from the list first.');
      return;
    }
    if (selected.isPublic) {
      toast.warn('Public recipes are read-only. Create your own copy via + New recipe.');
      return;
    }
    setEditorOpen('edit');
  }

  return (
    <>
      <div className="toolbar">
        <SearchBar pager={pager} placeholder="search your recipes (press Enter)" />
        <button type="button" className="btn" onClick={openEdit}>
          Edit selected
        </button>
        <button
          type="button"
          className="btn"
          disabled={!selected || remove.isPending}
          onClick={() => selected && remove.mutate(selected.id)}
        >
          Delete selected
        </button>
        <button type="button" className="btn btn-primary" onClick={() => setEditorOpen('new')}>
          + New recipe
        </button>
      </div>

      <div className="view-scroll">
        <div className="pad-16 stack-10">
          <DataTable
            columns={columns}
            rows={pager.items}
            rowKey={(r) => r.id}
            selectedKey={selected?.id ?? null}
            onSelect={setSelected}
            onActivate={openEdit}
            placeholder={
              pager.loading
                ? 'Loading…'
                : 'No recipes yet. Click + New recipe (or build one via the AI tab).'
            }
          />
          <LoadMoreBar pager={pager} />
        </div>
      </div>

      {editorOpen ? (
        <RecipeBuilderDialog
          editing={editorOpen === 'edit' ? selected : null}
          busy={create.isPending || update.isPending}
          onCancel={() => setEditorOpen(null)}
          onSubmit={(body) =>
            editorOpen === 'edit' && selected
              ? update.mutate({ id: selected.id, body })
              : create.mutate(body)
          }
        />
      ) : null}
    </>
  );
}

// ---------------- Foods ----------------

function FoodsTab() {
  const toast = useToast();
  const pager = usePager<Ingredient>((q, page, size, signal) =>
    api.listMyIngredients(q, page, size, signal),
  );
  const [selected, setSelected] = useState<Ingredient | null>(null);
  const [editorOpen, setEditorOpen] = useState<'new' | 'edit' | null>(null);

  const afterWrite = () => {
    setEditorOpen(null);
    setSelected(null);
    pager.refresh();
  };

  const create = useMutation({
    mutationFn: (body: CreateIngredientRequest) => api.createIngredient(body),
    onSuccess: afterWrite,
    onError: toast.showError,
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateIngredientRequest }) =>
      api.updateIngredient(id, body),
    onSuccess: afterWrite,
    onError: toast.showError,
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.deleteIngredient(id),
    onSuccess: afterWrite,
    onError: toast.showError,
  });

  const columns: Column<Ingredient>[] = [
    { key: 'name', header: 'Name', render: (i) => i.name },
    { key: 'brand', header: 'Brand', render: (i) => i.brand ?? '' },
    { key: 'kcal', header: 'kcal/100g', numeric: true, render: (i) => i.kcalPer100g },
    {
      key: 'p',
      header: 'Protein/100g',
      numeric: true,
      render: (i) => `${fmt(i.proteinPer100g)} g`,
    },
    { key: 'c', header: 'Carbs/100g', numeric: true, render: (i) => `${fmt(i.carbsPer100g)} g` },
    { key: 'f', header: 'Fat/100g', numeric: true, render: (i) => `${fmt(i.fatPer100g)} g` },
    { key: 'fib', header: 'Fiber/100g', numeric: true, render: (i) => `${fmt(i.fiberPer100g)} g` },
  ];

  function openEdit() {
    if (!selected) {
      toast.warn('Pick a food from the list first.');
      return;
    }
    if (selected.isPublic) {
      toast.warn('Public foods are read-only. Create your own copy via + New food.');
      return;
    }
    setEditorOpen('edit');
  }

  return (
    <>
      <div className="toolbar">
        <SearchBar pager={pager} placeholder="search your foods (press Enter)" />
        <button type="button" className="btn" onClick={openEdit}>
          Edit selected
        </button>
        <button
          type="button"
          className="btn"
          disabled={!selected || remove.isPending}
          onClick={() => selected && remove.mutate(selected.id)}
        >
          Delete selected
        </button>
        <button type="button" className="btn btn-primary" onClick={() => setEditorOpen('new')}>
          + New food
        </button>
      </div>

      <div className="view-scroll">
        <div className="pad-16 stack-10">
          <DataTable
            columns={columns}
            rows={pager.items}
            rowKey={(i) => i.id}
            selectedKey={selected?.id ?? null}
            onSelect={setSelected}
            onActivate={openEdit}
            placeholder={
              pager.loading
                ? 'Loading…'
                : 'No foods yet. Click + New food, or let the AI tab create some for you.'
            }
          />
          <LoadMoreBar pager={pager} />
        </div>
      </div>

      {editorOpen ? (
        <IngredientEditorDialog
          editing={editorOpen === 'edit' ? selected : null}
          busy={create.isPending || update.isPending}
          onCancel={() => setEditorOpen(null)}
          onSubmit={(body) =>
            editorOpen === 'edit' && selected
              ? update.mutate({ id: selected.id, body })
              : create.mutate(body)
          }
        />
      ) : null}
    </>
  );
}
