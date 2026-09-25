/**
 * Right-side "TODAY" card: calorie target + four macro rows shown as
 * "consumed / target" with a thin progress bar each. Port of SummaryCard.java.
 */

import type { DaySummary } from '../../api/types';
import { ProgressBar } from '../../components/ui';
import { fmt } from '../../lib/format';

function MacroRow({
  name,
  variant,
  consumed,
  target,
}: {
  name: string;
  variant: 'protein' | 'carbs' | 'fat' | 'fiber';
  consumed: number;
  target: number | null;
}) {
  const hasTarget = target != null && target > 0;
  return (
    <div className="stack-6">
      <div className="row">
        <span className="macro-name">{name}</span>
        <span className="spacer" />
        <span className={hasTarget ? 'macro-value' : 'macro-value-muted'}>
          {hasTarget ? `${fmt(consumed)} / ${target} g` : `${fmt(consumed)} g  (no target)`}
        </span>
      </div>
      <ProgressBar value={hasTarget ? consumed / target : 0} variant={variant} />
    </div>
  );
}

export function SummaryCard({ summary }: { summary: DaySummary }) {
  const consumed = summary.consumedKcal ?? 0;
  const target = summary.dailyCalorieTarget;

  return (
    <section className="card stack-10">
      <div className="card-title">TODAY</div>
      <div className="big-number">
        {target != null ? `${consumed} / ${target} kcal` : `${consumed} kcal`}
      </div>
      <ProgressBar value={target != null && target > 0 ? consumed / target : 0} />
      <div className="muted">
        {target != null
          ? `${summary.remainingKcal ?? 0} kcal remaining`
          : 'Set your profile + objective to compute a target'}
      </div>

      <div className="card-title" style={{ marginTop: 4 }}>
        MACROS
      </div>
      <div className="stack-16">
        <MacroRow
          name="Protein"
          variant="protein"
          consumed={summary.totalProtein}
          target={summary.proteinTargetG}
        />
        <MacroRow
          name="Carbs"
          variant="carbs"
          consumed={summary.totalCarbs}
          target={summary.carbsTargetG}
        />
        <MacroRow name="Fat" variant="fat" consumed={summary.totalFat} target={summary.fatTargetG} />
        <MacroRow
          name="Fiber"
          variant="fiber"
          consumed={summary.totalFiber}
          target={summary.fiberTargetG}
        />
      </div>
    </section>
  );
}
