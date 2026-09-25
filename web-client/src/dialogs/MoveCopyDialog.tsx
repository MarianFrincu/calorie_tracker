/** Pick a target date + meal for a move or copy. Port of MoveCopyDialog.java. */

import { useState } from 'react';

import { MEALS, type IsoDate, type Meal } from '../api/types';
import { Modal } from '../components/Modal';
import { Field } from '../components/ui';
import { prettyMeal } from '../lib/format';

interface Props {
  title: string;
  currentDate: IsoDate;
  currentMeal: Meal;
  onSubmit: (target: { date: IsoDate; meal: Meal }) => void;
  onCancel: () => void;
  busy?: boolean;
}

export function MoveCopyDialog({
  title,
  currentDate,
  currentMeal,
  onSubmit,
  onCancel,
  busy,
}: Props) {
  const [date, setDate] = useState<IsoDate>(currentDate);
  const [meal, setMeal] = useState<Meal>(currentMeal);

  return (
    <Modal
      title={title}
      headerText="Choose where this entry should end up."
      onOk={() => onSubmit({ date, meal })}
      onCancel={onCancel}
      busy={busy}
    >
      <Field label="Date">
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </Field>
      <Field label="Meal">
        <select value={meal} onChange={(e) => setMeal(e.target.value as Meal)}>
          {MEALS.map((m) => (
            <option key={m} value={m}>
              {prettyMeal(m)}
            </option>
          ))}
        </select>
      </Field>
    </Modal>
  );
}
