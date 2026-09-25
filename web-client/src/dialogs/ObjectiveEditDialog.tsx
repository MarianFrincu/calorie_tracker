/**
 * Modal editor for the objective, with a live preview of the resulting calorie
 * and macro targets. Port of ObjectiveView.ObjectiveEditDialog.
 */

import { useMemo, useState } from 'react';

import {
  GOALS,
  MACRO_PRESETS,
  type Goal,
  type MacroPreset,
  type Objective,
  type UpdateObjectiveRequest,
} from '../api/types';
import { Modal } from '../components/Modal';
import { NumberInput } from '../components/NumberInput';
import {
  GOAL_LABEL,
  MACRO_PRESET_LABEL,
  MAX_PROTEIN_G_PER_KG,
  belowBmr,
  dailyCalorieTarget,
  macroGrams,
  proteinCapped,
} from '../lib/nutrition';

const PERCENT_CHOICES = [10, 15, 20, 25, 30];

function snapPercent(value: number): number {
  return PERCENT_CHOICES.reduce((best, choice) =>
    Math.abs(value - choice) < Math.abs(value - best) ? choice : best,
  );
}

interface Props {
  initial: Objective;
  tdee: number;
  /** Calories burned at rest: a losing target may not go below it. */
  bmr: number;
  weightKg: number | null;
  busy?: boolean;
  onSubmit: (request: UpdateObjectiveRequest) => void;
  onCancel: () => void;
}

export function ObjectiveEditDialog({ initial, tdee, bmr, weightKg, busy, onSubmit, onCancel }: Props) {
  // Cuts that would go below BMR aren't offered for "Lose weight".
  const allowedLose = PERCENT_CHOICES.filter((p) => !belowBmr(tdee, bmr, p));
  const maxLose = allowedLose[allowedLose.length - 1] ?? PERCENT_CHOICES[0];

  const [goal, setGoal] = useState<Goal>(initial.goal ?? 'MAINTAIN');
  const [percent, setPercent] = useState(() => {
    const snapped = snapPercent(initial.goalPercent ?? 20);
    return initial.goal === 'LOSE' ? Math.min(snapped, maxLose) : snapped;
  });
  const [preset, setPreset] = useState<MacroPreset>(initial.macroPreset ?? 'BALANCED');
  const [fiber, setFiber] = useState<number | null>(initial.dailyFiberTargetG ?? 30);
  const [water, setWater] = useState<number | null>(initial.dailyWaterTargetMl ?? 2000);
  const fiberOk = fiber != null && fiber >= 0 && fiber <= 200;
  const waterOk = water != null && water >= 500 && water <= 6000;

  const preview = useMemo(() => {
    const effectivePercent = goal === 'MAINTAIN' ? 0 : percent;
    const kcal = dailyCalorieTarget(tdee, goal, effectivePercent, bmr) ?? 0;
    return { kcal, capped: proteinCapped(kcal, preset, weightKg), ...macroGrams(kcal, preset, weightKg) };
  }, [goal, percent, preset, tdee, bmr, weightKg]);

  const chooseGoal = (next: Goal) => {
    setGoal(next);
    if (next === 'LOSE' && percent > maxLose) setPercent(maxLose);
  };

  return (
    <Modal
      title="Change objective"
      headerText="Pick a goal, intensity and macro distribution. The new values apply from today onwards; past days keep their historical target."
      size="wide"
      busy={busy}
      okDisabled={!fiberOk || !waterOk}
      onCancel={onCancel}
      onOk={() =>
        onSubmit({
          goal,
          goalPercent: goal === 'MAINTAIN' ? 0 : percent,
          macroPreset: preset,
          dailyFiberTargetG: fiber!,
          dailyWaterTargetMl: water!,
        })
      }
    >
      <div className="form-grid">
        <label htmlFor="o-goal">Goal</label>
        <select id="o-goal" value={goal} onChange={(e) => chooseGoal(e.target.value as Goal)}>
          {GOALS.map((g) => (
            <option key={g} value={g}>
              {GOAL_LABEL[g]}
            </option>
          ))}
        </select>

        <label htmlFor="o-percent">Intensity</label>
        <select
          id="o-percent"
          value={percent}
          disabled={goal === 'MAINTAIN'}
          onChange={(e) => setPercent(Number(e.target.value))}
        >
          {PERCENT_CHOICES.map((p) => {
            const tooLow = goal === 'LOSE' && p > maxLose;
            return (
              <option key={p} value={p} disabled={tooLow}>
                {tooLow ? `${p}% (below your BMR)` : `${p}%`}
              </option>
            );
          })}
        </select>

        <label htmlFor="o-preset">Macro distribution</label>
        <select
          id="o-preset"
          value={preset}
          onChange={(e) => setPreset(e.target.value as MacroPreset)}
        >
          {MACRO_PRESETS.map((p) => (
            <option key={p} value={p}>
              {MACRO_PRESET_LABEL[p]}
            </option>
          ))}
        </select>

        <label htmlFor="o-fiber">Daily fiber (g)</label>
        <NumberInput id="o-fiber" integer min={0} max={200} value={fiber} onChange={setFiber} />

        <label htmlFor="o-water">Daily water (ml)</label>
        <NumberInput id="o-water" integer min={500} max={6000} value={water} onChange={setWater} />
      </div>

      {!fiberOk || !waterOk ? (
        <div className="notice warn" role="alert">
          {!fiberOk ? 'Daily fiber must be a number from 0 to 200 g. ' : ''}
          {!waterOk ? 'Daily water must be a number from 500 to 6000 ml.' : ''}
        </div>
      ) : null}

      <p className="muted">
        Intensity is how much of your maintenance calories (TDEE, {tdee} kcal) you cut to lose
        weight or add to gain it. It isn't used for Maintain.
      </p>

      {goal === 'LOSE' ? (
        <div className="notice warn" role="note">
          <strong>Your target never goes below your BMR ({bmr} kcal).</strong> That's what your
          body burns at complete rest; eating less slows your metabolism and costs muscle. At
          your activity level the deepest safe cut is {maxLose}%.
        </div>
      ) : null}

      {preview.capped && weightKg != null ? (
        <div className="notice" role="note">
          Protein is capped at {MAX_PROTEIN_G_PER_KG} g per kg of body weight (
          {Math.round(MAX_PROTEIN_G_PER_KG * weightKg)} g for {weightKg} kg). More brings no extra
          benefit, so the rest of those calories go to carbs and fat.
        </div>
      ) : null}

      <div className="preview-row">
        <span className="muted">Preview:</span>
        <span className="medium-number">
          {preview.kcal} kcal/day · P {preview.protein} g · C {preview.carbs} g · F {preview.fat} g
        </span>
      </div>
    </Modal>
  );
}
