/**
 * "Recipe blueprint" response bubble: editable name + cooked weight, per-
 * ingredient rows on the per-100 g basis, live raw/per-100 g totals, and a
 * one-click "Save as recipe".
 */

import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { ParsedRecipe } from '../../api/types';
import { useToast } from '../../components/Toast';
import { MacroTags } from '../../components/ui';
import { fmt, parseNumberOr } from '../../lib/format';

export function RecipeBubble({ result }: { result: ParsedRecipe }) {
  const toast = useToast();
  const queryClient = useQueryClient();

  const ingredients = result.ingredients ?? [];
  const rawGrams = useMemo(
    () => ingredients.reduce((sum, i) => sum + i.amountGrams, 0),
    [ingredients],
  );

  const [name, setName] = useState(result.name || 'My recipe');
  const [cookedGrams, setCookedGrams] = useState(rawGrams > 0 ? String(Math.round(rawGrams)) : '');
  const [savedName, setSavedName] = useState<string | null>(null);

  const totals = useMemo(
    () =>
      ingredients.reduce(
        (acc, i) => {
          const factor = i.amountGrams / 100;
          acc.kcal += i.kcalPer100g * factor;
          acc.protein += i.proteinPer100g * factor;
          acc.carbs += i.carbsPer100g * factor;
          acc.fat += i.fatPer100g * factor;
          acc.fiber += i.fiberPer100g * factor;
          return acc;
        },
        { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0 },
      ),
    [ingredients],
  );

  const cooked = parseNumberOr(cookedGrams, rawGrams);
  const per100 = cooked > 0 ? 100 / cooked : 0;

  const save = useMutation({
    mutationFn: () =>
      api.saveAiRecipe({
        name: name.trim() || 'My recipe',
        servings: 1,
        ingredients,
        totalCookedGrams: cooked > 0 ? cooked : null,
      }),
    onSuccess: (saved) => {
      setSavedName(saved.name);
      void queryClient.invalidateQueries({ queryKey: ['recipes'] });
      toast.info(`Saved "${saved.name}" to your recipes.`);
    },
    onError: toast.showError,
  });

  return (
    <div className="chat-row">
      <div className="chat-ai wide">
        <div className="card-title">
          Recipe blueprint — edit name + cooked weight before saving if you like
        </div>

        <div className="row wrap">
          <label className="muted nowrap" htmlFor="ai-recipe-name">
            Name:
          </label>
          <input
            id="ai-recipe-name"
            type="text"
            className="grow"
            style={{ minWidth: 240 }}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <label className="muted nowrap" htmlFor="ai-recipe-cooked">
            Cooked (g):
          </label>
          <input
            id="ai-recipe-cooked"
            type="number"
            min={0}
            step={1}
            style={{ width: 120 }}
            placeholder="auto from sum of raw"
            value={cookedGrams}
            onChange={(e) => setCookedGrams(e.target.value)}
          />
        </div>

        <p className="muted">
          Cooked weight = raw sum by default. Override if cooking changes the dish weight (pasta
          absorbs water, meat loses it). Per-100 g values below are based on the cooked weight.
        </p>

        {ingredients.length === 0 ? (
          <p className="muted">(nothing detected)</p>
        ) : (
          ingredients.map((i, index) => (
            <div className="entry-row" key={`${i.name}-${index}`}>
              <div className="col grow" style={{ gap: 2 }}>
                <span className="entry-name">{i.name}</span>
                <span className="entry-meta">
                  {fmt(i.amountGrams)} g · {i.kcalPer100g} kcal/100g
                </span>
              </div>
              <MacroTags
                protein={i.proteinPer100g}
                carbs={i.carbsPer100g}
                fat={i.fatPer100g}
                fiber={i.fiberPer100g}
                suffix="/100g"
              />
            </div>
          ))
        )}

        <p className="entry-name">
          Raw totals: {Math.round(totals.kcal)} kcal · P {totals.protein.toFixed(1)} g · C{' '}
          {totals.carbs.toFixed(1)} g · F {totals.fat.toFixed(1)} g · Fiber{' '}
          {totals.fiber.toFixed(1)} g (raw {Math.round(rawGrams)} g)
        </p>
        <p className="entry-name">
          {cooked <= 0
            ? 'Per 100 g (cooked): —'
            : `Per 100 g (cooked): ${Math.round(totals.kcal * per100)} kcal · P ${(
                totals.protein * per100
              ).toFixed(1)} g · C ${(totals.carbs * per100).toFixed(1)} g · F ${(
                totals.fat * per100
              ).toFixed(1)} g · Fiber ${(totals.fiber * per100).toFixed(1)} g (cooked ${Math.round(
                cooked,
              )} g)`}
        </p>

        <div className="row">
          <button
            type="button"
            className="btn btn-success"
            onClick={() => save.mutate()}
            disabled={save.isPending || savedName != null || ingredients.length === 0}
          >
            {savedName
              ? `Saved as "${savedName}"`
              : save.isPending
                ? 'Saving…'
                : 'Save as recipe'}
          </button>
        </div>
      </div>
    </div>
  );
}
