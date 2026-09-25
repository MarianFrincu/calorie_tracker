/**
 * "New food" / "Edit food" dialog: full per-100 g nutrition form.
 *
 * Every macro must be entered explicitly (typing 0 is valid, blank is not) and
 * the calorie value is cross-checked against `4P + 4C + 9F` within ±10 kcal so
 * blatantly inconsistent rows can't enter the library. Port of
 * IngredientEditorDialog.java.
 */

import { useState } from 'react';

import type { CreateIngredientRequest, Ingredient } from '../api/types';
import { Modal } from '../components/Modal';
import { KCAL_TOLERANCE, impliedKcal } from '../lib/nutrition';

interface FormState {
  name: string;
  brand: string;
  kcal: string;
  protein: string;
  carbs: string;
  fat: string;
  fiber: string;
}

function stripZero(value: number): string {
  return Number.isInteger(value) ? String(value) : String(value);
}

function initialState(editing: Ingredient | null): FormState {
  if (!editing) {
    return { name: '', brand: '', kcal: '', protein: '', carbs: '', fat: '', fiber: '' };
  }
  return {
    name: editing.name ?? '',
    brand: editing.brand ?? '',
    kcal: String(editing.kcalPer100g),
    protein: stripZero(editing.proteinPer100g),
    carbs: stripZero(editing.carbsPer100g),
    fat: stripZero(editing.fatPer100g),
    fiber: stripZero(editing.fiberPer100g),
  };
}

function num(raw: string): number | null {
  const trimmed = raw.trim().replace(',', '.');
  if (trimmed === '') return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}

/** @returns a user-facing error, or null when the form is valid. */
function validate(form: FormState): string | null {
  if (!form.name.trim()) return 'Name is required.';
  if (!form.kcal.trim()) return 'kcal is required (type 0 if you mean zero).';
  if (!form.protein.trim()) return 'Protein is required (type 0 if you mean zero).';
  if (!form.carbs.trim()) return 'Carbs is required (type 0 if you mean zero).';
  if (!form.fat.trim()) return 'Fat is required (type 0 if you mean zero).';
  if (!form.fiber.trim()) return 'Fiber is required (type 0 if you mean zero).';

  const kcal = num(form.kcal);
  const p = num(form.protein);
  const c = num(form.carbs);
  const f = num(form.fat);
  const fib = num(form.fiber);
  if (kcal == null || p == null || c == null || f == null || fib == null) {
    return "One of the numeric fields isn't a number. Use a dot for decimals.";
  }
  if (kcal < 0 || p < 0 || c < 0 || f < 0 || fib < 0) return "Values can't be negative.";
  if (p > 100 || c > 100 || f > 100 || fib > 100) {
    return "Macros per 100 g can't exceed 100.";
  }
  if (p + c + f > 100.5) return "Protein + carbs + fat per 100 g can't exceed 100 g.";

  const expected = impliedKcal(p, c, f);
  if (expected < kcal - KCAL_TOLERANCE || expected > kcal + KCAL_TOLERANCE) {
    return (
      `kcal doesn't match the macros. Macros give ~${Math.round(expected)} kcal ` +
      `but you entered ${Math.round(kcal)}. Adjust either to within ${KCAL_TOLERANCE} kcal.`
    );
  }
  return null;
}

interface Props {
  editing?: Ingredient | null;
  busy?: boolean;
  onSubmit: (request: CreateIngredientRequest) => void;
  onCancel: () => void;
}

export function IngredientEditorDialog({ editing = null, busy, onSubmit, onCancel }: Props) {
  const [form, setForm] = useState<FormState>(() => initialState(editing));
  const [error, setError] = useState<string | null>(null);

  const isEdit = editing != null;
  const kcal = num(form.kcal);
  const p = num(form.protein);
  const c = num(form.carbs);
  const f = num(form.fat);
  const implied = p != null && c != null && f != null ? impliedKcal(p, c, f) : null;

  function submit() {
    const problem = validate(form);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    onSubmit({
      name: form.name.trim(),
      brand: form.brand.trim() === '' ? null : form.brand.trim(),
      kcalPer100g: Math.round(num(form.kcal)!),
      proteinPer100g: num(form.protein)!,
      carbsPer100g: num(form.carbs)!,
      fatPer100g: num(form.fat)!,
      fiberPer100g: num(form.fiber)!,
    });
  }

  const set = (key: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, [key]: e.target.value }));
    setError(null);
  };

  return (
    <Modal
      title={isEdit ? 'Edit food' : 'New food'}
      headerText={
        isEdit
          ? 'Per-100 g values. Public seed foods are read-only — edit your own copy.'
          : 'All values are per 100 g of the food (check the label).'
      }
      busy={busy}
      onOk={submit}
      onCancel={onCancel}
    >
      <div className="form-grid">
        <label htmlFor="i-name">Name</label>
        <input
          id="i-name"
          type="text"
          placeholder="e.g. Greek yogurt, full fat"
          value={form.name}
          onChange={set('name')}
        />

        <label htmlFor="i-brand">Brand</label>
        <input
          id="i-brand"
          type="text"
          placeholder="optional"
          value={form.brand}
          onChange={set('brand')}
        />

        <label htmlFor="i-kcal">kcal per 100 g</label>
        <input
          id="i-kcal"
          type="number"
          min={0}
          max={1500}
          step={1}
          placeholder="required"
          value={form.kcal}
          onChange={set('kcal')}
        />

        <label htmlFor="i-protein">Protein (g)</label>
        <input
          id="i-protein"
          type="number"
          min={0}
          max={100}
          step={0.1}
          placeholder="required"
          value={form.protein}
          onChange={set('protein')}
        />

        <label htmlFor="i-carbs">Carbs (g)</label>
        <input
          id="i-carbs"
          type="number"
          min={0}
          max={100}
          step={0.1}
          placeholder="required"
          value={form.carbs}
          onChange={set('carbs')}
        />

        <label htmlFor="i-fat">Fat (g)</label>
        <input
          id="i-fat"
          type="number"
          min={0}
          max={100}
          step={0.1}
          placeholder="required"
          value={form.fat}
          onChange={set('fat')}
        />

        <label htmlFor="i-fiber">Fiber (g)</label>
        <input
          id="i-fiber"
          type="number"
          min={0}
          max={100}
          step={0.1}
          placeholder="required"
          value={form.fiber}
          onChange={set('fiber')}
        />
      </div>

      {implied != null ? (
        <div className="preview-row">
          <span className="muted">Macros imply:</span>
          <span className="medium-number">{Math.round(implied)} kcal</span>
          {kcal != null ? (
            <span className="muted">
              (you entered {Math.round(kcal)} — tolerance ±{KCAL_TOLERANCE})
            </span>
          ) : null}
        </div>
      ) : null}

      {error ? <div className="inline-hint">{error}</div> : null}
    </Modal>
  );
}
