/**
 * Body stats only, with a live BMR / TDEE preview as the user types. Goal and
 * macro settings live in the Objective view. Port of ProfileView.java.
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../api/client';
import {
  ACTIVITY_LEVELS,
  SEXES,
  type ActivityLevel,
  type Sex,
  type UpdateProfileRequest,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Modal } from '../components/Modal';
import { NumberInput } from '../components/NumberInput';
import { friendlyMessage, useToast } from '../components/Toast';
import { Loading } from '../components/ui';
import { today } from '../lib/format';
import { ACTIVITY_LABEL, SEX_LABEL, bmr, tdeeMaintain } from '../lib/nutrition';

interface FormState {
  displayName: string;
  sex: Sex;
  /** null while the field is empty. */
  age: number | null;
  heightCm: number | null;
  weightKg: number | null;
  activityLevel: ActivityLevel;
}

const DEFAULTS: FormState = {
  displayName: '',
  sex: 'MALE',
  age: 25,
  heightCm: 175,
  weightKg: 75,
  activityLevel: 'MODERATE',
};

export function ProfileView() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: ({ signal }) => api.getProfile(signal),
  });

  // The latest weight-log entry is the single source of truth for current body
  // weight, so it overrides profile.weightKg once loaded.
  const weightQuery = useQuery({
    queryKey: ['weight'],
    queryFn: ({ signal }) => api.listWeight(signal),
  });

  const [form, setForm] = useState<FormState>(DEFAULTS);
  const [lastSavedBmr, setLastSavedBmr] = useState<number | null>(null);
  const [incomplete, setIncomplete] = useState(false);
  // The weight list can arrive after the form is shown; once the user has
  // typed a weight, that late prefill must not overwrite it.
  const weightEdited = useRef(false);

  useEffect(() => {
    const p = profileQuery.data;
    if (!p) return;
    setForm((current) => ({
      displayName: p.displayName ?? current.displayName,
      sex: p.sex ?? current.sex,
      age: p.age ?? current.age,
      heightCm: p.heightCm ?? current.heightCm,
      weightKg: p.weightKg ?? current.weightKg,
      activityLevel: p.activityLevel ?? current.activityLevel,
    }));
    setLastSavedBmr(p.bmr);
    setIncomplete(
      p.sex == null ||
        p.age == null ||
        p.heightCm == null ||
        p.weightKg == null ||
        p.activityLevel == null,
    );
  }, [profileQuery.data]);

  useEffect(() => {
    const list = weightQuery.data;
    if (!list || list.length === 0 || weightEdited.current) return;
    const latest = list[list.length - 1];
    if (latest.weightKg != null) {
      setForm((current) => ({ ...current, weightKg: latest.weightKg! }));
    }
  }, [weightQuery.data]);

  const previewBmr = useMemo(
    () => bmr(form.sex, form.age, form.heightCm, form.weightKg),
    [form.sex, form.age, form.heightCm, form.weightKg],
  );
  const previewTdee = useMemo(
    () => tdeeMaintain(form.sex, form.age, form.heightCm, form.weightKg, form.activityLevel),
    [form.sex, form.age, form.heightCm, form.weightKg, form.activityLevel],
  );

  const save = useMutation({
    mutationFn: async (body: UpdateProfileRequest) => {
      const saved = await api.updateProfile(body);
      // Also log today's weight so Profile and the Weight tab never diverge —
      // upsertWeight is idempotent per (user, date).
      if (body.weightKg != null) {
        await api.upsertWeight(today(), body.weightKg);
      }
      return saved;
    },
    onSuccess: (saved) => {
      setLastSavedBmr(saved.bmr);
      setIncomplete(false);
      void queryClient.invalidateQueries({ queryKey: ['profile'] });
      void queryClient.invalidateQueries({ queryKey: ['objective'] });
      void queryClient.invalidateQueries({ queryKey: ['weight'] });
      void queryClient.invalidateQueries({ queryKey: ['summary'] });
      toast.info(
        `Saved. BMR ${saved.bmr ?? '-'} kcal, maintenance TDEE ${saved.tdeeMaintain ?? '-'} kcal.`,
      );
    },
    onError: toast.showError,
  });

  if (profileQuery.isPending) return <Loading what="your profile" />;

  const dirty = lastSavedBmr != null && previewBmr != null && previewBmr !== lastSavedBmr;

  return (
    <div className="view-scroll">
      <div className="pad-20 stack-16 center-column">
        {incomplete ? (
          <div className="onboarding-banner">
            Welcome! Fill in your sex, age, height, weight and activity level, then press Save. The
            other tabs unlock once your profile is complete.
          </div>
        ) : null}

        <section className="target-card stack-12">
          <div>
            <div className="card-title">TDEE (MAINTAIN)</div>
            <div className="big-number">{previewTdee != null ? `${previewTdee} kcal` : '-'}</div>
            <div className="muted">kcal/day to keep weight stable</div>
          </div>
          <div>
            <div className="card-title">BMR</div>
            <div className="medium-number">{previewBmr != null ? `${previewBmr} kcal` : '-'}</div>
            <div className="muted">kcal/day burned at rest</div>
          </div>
        </section>

        <section className="card stack-16">
          <h2 className="section-title">Your stats</h2>
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate({
                displayName: form.displayName.trim() === '' ? null : form.displayName.trim(),
                sex: form.sex,
                age: form.age,
                heightCm: form.heightCm,
                weightKg: form.weightKg,
                activityLevel: form.activityLevel,
              });
            }}
          >
            <label htmlFor="p-name">Display name</label>
            <input
              id="p-name"
              type="text"
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            />

            <label htmlFor="p-sex">Sex</label>
            <select
              id="p-sex"
              value={form.sex}
              onChange={(e) => setForm({ ...form, sex: e.target.value as Sex })}
            >
              {SEXES.map((s) => (
                <option key={s} value={s}>
                  {SEX_LABEL[s]}
                </option>
              ))}
            </select>

            <label htmlFor="p-age">Age</label>
            <NumberInput
              id="p-age"
              integer
              required
              min={5}
              max={110}
              value={form.age}
              onChange={(age) => setForm({ ...form, age })}
            />

            <label htmlFor="p-height">Height (cm)</label>
            <NumberInput
              id="p-height"
              required
              min={120}
              max={230}
              value={form.heightCm}
              onChange={(heightCm) => setForm({ ...form, heightCm })}
            />

            <label htmlFor="p-weight">Weight (kg)</label>
            <NumberInput
              id="p-weight"
              required
              min={30}
              max={250}
              value={form.weightKg}
              onChange={(weightKg) => {
                weightEdited.current = true;
                setForm({ ...form, weightKg });
              }}
            />

            <label htmlFor="p-activity">Activity level</label>
            <select
              id="p-activity"
              value={form.activityLevel}
              onChange={(e) => setForm({ ...form, activityLevel: e.target.value as ActivityLevel })}
            >
              {ACTIVITY_LEVELS.map((a) => (
                <option key={a} value={a}>
                  {ACTIVITY_LABEL[a]}
                </option>
              ))}
            </select>

            <span />
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={save.isPending}>
                {save.isPending ? 'Saving…' : 'Save profile'}
              </button>
              {dirty ? (
                <span className="muted">Unsaved changes (saved BMR: {lastSavedBmr} kcal)</span>
              ) : null}
            </div>
          </form>
        </section>

        <section className="card stack-10">
          <h2 className="section-title">How these numbers are calculated</h2>
          <pre className="formula-block">
{`BMR (Mifflin-St Jeor) — kcal/day burned at complete rest:
    Male:   BMR = 10 × weight(kg) + 6.25 × height(cm) − 5 × age + 5
    Female: BMR = 10 × weight(kg) + 6.25 × height(cm) − 5 × age − 161`}
          </pre>
          <pre className="formula-block">
{`TDEE = BMR × activity multiplier   (kcal/day to maintain weight)
    Sedentary  × 1.20   (little or no exercise)
    Light      × 1.375  (1-3 light workouts/week)
    Moderate   × 1.55   (3-5 workouts/week)
    Active     × 1.725  (6-7 workouts/week)
    Very high  × 1.90   (twice a day / physical job)`}
          </pre>
          <p className="muted">
            Your daily calorie target on the Objective tab is TDEE adjusted by the percent you pick
            (e.g. −20% for a cut, +15% for a lean bulk).
          </p>
        </section>

        <DeleteAccountSection />
      </div>
    </div>
  );
}

/** Irreversible: removes every diary entry, food, recipe, weight and the account itself. */
function DeleteAccountSection() {
  const auth = useAuth();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmDelete() {
    setBusy(true);
    setError(null);
    try {
      await auth.deleteAccount();
      setOpen(false);
      // cognito mode: the session is gone and the app is back on the sign-in
      // screen. dev mode: the (now empty) profile reloads into onboarding.
    } catch (err) {
      setError(friendlyMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card stack-10 danger-zone">
      <h2 className="section-title">Delete account</h2>
      <p className="muted">
        Permanently deletes your profile, diary, water and weight history, objectives, and your own
        foods and recipes{auth.mode === 'cognito' ? ', then your login' : ''}. This cannot be undone.
      </p>
      <div>
        <button
          type="button"
          className="btn btn-danger"
          onClick={() => {
            setTyped('');
            setError(null);
            setOpen(true);
          }}
        >
          Delete my account…
        </button>
      </div>
      {open ? (
        <Modal
          title="Delete account"
          headerText="Everything will be erased immediately. Type DELETE to confirm."
          okLabel="Delete forever"
          okDisabled={typed !== 'DELETE'}
          busy={busy}
          onOk={() => void confirmDelete()}
          onCancel={() => setOpen(false)}
        >
          <input
            aria-label="Type DELETE to confirm"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            placeholder="DELETE"
          />
          {error ? <div className="inline-hint">{error}</div> : null}
        </Modal>
      ) : null}
    </section>
  );
}
