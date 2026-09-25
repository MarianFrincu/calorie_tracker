/**
 * "Parsed for diary" response bubble: one row per detected item with a library
 * badge, totals, and an "Add all to diary" action bar.
 *
 * The badge mirrors the desktop client: each parsed item is looked up in the
 * library and shown as "in app library" (blue), "in my library" (green), or
 * "not in any library — AI estimate" (amber) with a save button.
 */

import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '../../api/client';
import { MEALS, type Meal, type ParseResult, type ParsedIngredient } from '../../api/types';
import { useToast } from '../../components/Toast';
import { MacroTag, MacroTags } from '../../components/ui';
import { defaultMealForNow, fmt, parseGrams, prettyMeal, round1, today } from '../../lib/format';

type Origin = 'checking' | 'app' | 'mine' | 'missing';

function ItemRow({ item }: { item: ParsedIngredient }) {
  const toast = useToast();
  const [origin, setOrigin] = useState<Origin>('checking');
  const [saved, setSaved] = useState(false);

  // Probe the library for an exact-name match; the first hit's `isPublic`
  // tells us whether it came from the app library or the user's own.
  useEffect(() => {
    const controller = new AbortController();
    api
      .searchAllIngredients(item.name, 0, 1, controller.signal)
      .then((hits) => {
        if (controller.signal.aborted) return;
        const exact =
          hits.length > 0 && hits[0].name.trim().toLowerCase() === item.name.trim().toLowerCase();
        setOrigin(exact ? (hits[0].isPublic ? 'app' : 'mine') : 'missing');
      })
      .catch(() => {
        if (!controller.signal.aborted) setOrigin('missing');
      });
    return () => controller.abort();
  }, [item.name]);

  const saveToLibrary = useMutation({
    mutationFn: () => {
      // The AI reports per-portion values; rescale to per-100 g when the
      // quantity is expressed as a mass, otherwise store as-is.
      const grams = parseGrams(item.quantity);
      const scale = grams > 0 ? 100 / grams : 1;
      return api.createIngredient({
        name: item.name,
        brand: null,
        kcalPer100g: Math.round(item.calories * scale),
        proteinPer100g: round1(item.protein * scale),
        carbsPer100g: round1(item.carbs * scale),
        fatPer100g: round1(item.fat * scale),
        fiberPer100g: round1(item.fiber * scale),
      });
    },
    onSuccess: () => {
      setSaved(true);
      setOrigin('mine');
    },
    onError: toast.showError,
  });

  const badge =
    origin === 'checking' ? (
      <MacroTag text="checking library…" variant="library-check" />
    ) : origin === 'app' ? (
      <MacroTag text="in app library" variant="library-app" />
    ) : origin === 'mine' ? (
      <MacroTag text="in my library" variant="library-mine" />
    ) : (
      <MacroTag text="not in any library — AI estimate" variant="library-missing" />
    );

  return (
    <div className="entry-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 6 }}>
      <div className="row">
        <span className="entry-name grow">
          {item.name}
          {item.quantity ? `  ×  ${item.quantity}` : ''}
        </span>
        <span className="entry-kcal">{item.calories} kcal</span>
      </div>
      <MacroTags
        protein={item.protein}
        carbs={item.carbs}
        fat={item.fat}
        fiber={item.fiber}
        suffix=" g"
      />
      <div className="row wrap">
        {badge}
        {origin === 'missing' && !saved ? (
          <button
            type="button"
            className="btn-add"
            style={{ width: 'auto' }}
            onClick={() => saveToLibrary.mutate()}
            disabled={saveToLibrary.isPending}
          >
            {saveToLibrary.isPending ? 'Saving…' : 'Save to my library'}
          </button>
        ) : null}
        {saved ? <span className="muted">Saved!</span> : null}
      </div>
    </div>
  );
}

export function DiaryBubble({ result }: { result: ParseResult }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [date, setDate] = useState(today());
  const [meal, setMeal] = useState<Meal>(defaultMealForNow);
  const [added, setAdded] = useState(false);

  const addAll = useMutation({
    mutationFn: async () => {
      // The parser returns per-portion values, so each row is logged as a
      // freeform ("custom") diary entry rather than a library reference.
      for (const item of result.items) {
        await api.addDiary({
          date,
          meal,
          customName: item.name + (item.quantity ? ` x${item.quantity}` : ''),
          customKcal: item.calories,
          customProtein: item.protein,
          customCarbs: item.carbs,
          customFat: item.fat,
          customFiber: item.fiber,
        });
      }
    },
    onSuccess: () => {
      setAdded(true);
      void queryClient.invalidateQueries({ queryKey: ['summary'] });
      void queryClient.invalidateQueries({ queryKey: ['report'] });
    },
    onError: toast.showError,
  });

  return (
    <div className="chat-row">
      <div className="chat-ai wide">
        <div className="card-title">Parsed for diary</div>

        {result.items.length === 0 ? (
          <p className="muted">(nothing detected)</p>
        ) : (
          result.items.map((item, i) => <ItemRow key={`${item.name}-${i}`} item={item} />)
        )}

        <p className="entry-name">
          Totals: {result.totalCalories} kcal · P {fmt(result.totalProtein)} g · C{' '}
          {fmt(result.totalCarbs)} g · F {fmt(result.totalFat)} g · Fiber {fmt(result.totalFiber)} g
        </p>

        <div className="row wrap">
          <label className="muted nowrap" htmlFor={`ai-date-${date}`}>
            Date:
          </label>
          <input
            id={`ai-date-${date}`}
            type="date"
            className="field-inline"
            value={date}
            onChange={(e) => e.target.value && setDate(e.target.value)}
          />
          <label className="muted nowrap" htmlFor="ai-meal">
            Meal:
          </label>
          <select
            id="ai-meal"
            className="field-inline"
            value={meal}
            onChange={(e) => setMeal(e.target.value as Meal)}
          >
            {MEALS.map((m) => (
              <option key={m} value={m}>
                {prettyMeal(m)}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn btn-success"
            onClick={() => addAll.mutate()}
            disabled={addAll.isPending || added || result.items.length === 0}
          >
            {added ? 'Added!' : addAll.isPending ? 'Saving…' : 'Add all to diary'}
          </button>
        </div>
      </div>
    </div>
  );
}
