/**
 * Read-only summary of the current objective plus a modal editor. Changing the
 * objective applies from today onward; past days keep their historical
 * snapshot. Port of ObjectiveView.java.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../api/client';
import type { UpdateObjectiveRequest } from '../api/types';
import { ObjectiveEditDialog } from '../dialogs/ObjectiveEditDialog';
import { useToast } from '../components/Toast';
import { Loading, MacroTag } from '../components/ui';
import { GOAL_LABEL, MACRO_PRESET_LABEL } from '../lib/nutrition';

export function ObjectiveView() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);

  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: ({ signal }) => api.getProfile(signal),
  });
  const objectiveQuery = useQuery({
    queryKey: ['objective'],
    queryFn: ({ signal }) => api.getObjective(signal),
  });

  const save = useMutation({
    mutationFn: (body: UpdateObjectiveRequest) => api.updateObjective(body),
    onSuccess: () => {
      setEditing(false);
      void queryClient.invalidateQueries({ queryKey: ['objective'] });
      void queryClient.invalidateQueries({ queryKey: ['summary'] });
      void queryClient.invalidateQueries({ queryKey: ['report'] });
      toast.info('Objective updated — it applies from today onward.');
    },
    onError: toast.showError,
  });

  if (profileQuery.isPending || objectiveQuery.isPending) return <Loading what="your objective" />;

  const objective = objectiveQuery.data;
  const tdee = profileQuery.data?.tdeeMaintain ?? null;
  const target = objective?.dailyCalorieTarget ?? null;

  const direction =
    objective == null
      ? '-'
      : objective.goal === 'LOSE'
        ? `${objective.goalPercent ?? '?'}% under TDEE`
        : objective.goal === 'GAIN'
          ? `${objective.goalPercent ?? '?'}% over TDEE`
          : 'at TDEE';

  return (
    <div className="view-scroll">
      <div className="pad-20 stack-16 center-column">
        <section className="target-card stack-10">
          <div className="card-title">DAILY CALORIE TARGET</div>
          <div className="big-number">{target != null ? `${target} kcal/day` : '-'}</div>
          <div className="muted">
            {target != null && tdee != null
              ? objective?.goal === 'LOSE' && target === profileQuery.data?.bmr
                ? `Held at your BMR, the lowest safe target (TDEE ${tdee} kcal)`
                : `Currently ${direction} (TDEE ${tdee} kcal)`
              : 'Set every field on the Profile tab first.'}
          </div>

          <div className="card-title" style={{ marginTop: 6 }}>
            MACRO TARGETS (GRAMS)
          </div>
          <div className="stack-6">
            <div className="row">
              <MacroTag text="PROTEIN" variant="protein" />
              <span className="medium-number">{objective?.dailyProteinTargetG ?? '-'} g</span>
            </div>
            <div className="row">
              <MacroTag text="CARBS" variant="carbs" />
              <span className="medium-number">{objective?.dailyCarbsTargetG ?? '-'} g</span>
            </div>
            <div className="row">
              <MacroTag text="FAT" variant="fat" />
              <span className="medium-number">{objective?.dailyFatTargetG ?? '-'} g</span>
            </div>
          </div>
        </section>

        <section className="card stack-12">
          <h2 className="section-title">Current objective</h2>
          <div className="form-grid">
            <span className="muted">Goal</span>
            <span>{objective?.goal ? GOAL_LABEL[objective.goal] : '-'}</span>

            <span className="muted">Intensity</span>
            <span>
              {objective?.goal === 'MAINTAIN'
                ? 'at TDEE'
                : objective?.goalPercent != null
                  ? `${objective.goalPercent}%`
                  : '-'}
            </span>

            <span className="muted">Macro distribution</span>
            <span>{objective?.macroPreset ? MACRO_PRESET_LABEL[objective.macroPreset] : '-'}</span>

            <span className="muted">Daily fiber target</span>
            <span>
              {objective?.dailyFiberTargetG != null ? `${objective.dailyFiberTargetG} g` : '-'}
            </span>

            <span className="muted">Daily water target</span>
            <span>
              {objective?.dailyWaterTargetMl != null ? `${objective.dailyWaterTargetMl} ml` : '-'}
            </span>
          </div>

          <p className="muted">
            Changing the objective applies from today onward. Past days keep the objective that was
            in effect at the time — they aren't rewritten.
          </p>

          <div className="row">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                if (tdee == null || objective == null) {
                  toast.warn('Profile + objective are still loading. Try again in a moment.');
                  return;
                }
                setEditing(true);
              }}
            >
              Change objective…
            </button>
          </div>
        </section>
      </div>

      {editing && objective && tdee != null && profileQuery.data?.bmr != null ? (
        <ObjectiveEditDialog
          initial={objective}
          tdee={tdee}
          bmr={profileQuery.data.bmr}
          weightKg={profileQuery.data.weightKg}
          busy={save.isPending}
          onCancel={() => setEditing(false)}
          onSubmit={(request) => save.mutate(request)}
        />
      ) : null}
    </div>
  );
}
