/**
 * Right-side WATER card: totals, quick + custom add, and per-entry delete
 * chips. Port of WaterCard.java.
 */

import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { IsoDate, WaterSummary } from '../../api/types';
import { ProgressBar } from '../../components/ui';
import { useToast } from '../../components/Toast';

export function WaterCard({ water, date }: { water: WaterSummary; date: IsoDate }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [custom, setCustom] = useState('');

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['summary', date] });
    void queryClient.invalidateQueries({ queryKey: ['report'] });
  };

  const addWater = useMutation({
    mutationFn: (ml: number) => api.addWater(date, ml),
    onSuccess: invalidate,
    onError: toast.showError,
  });

  const removeWater = useMutation({
    mutationFn: (id: number) => api.deleteWater(id),
    onSuccess: invalidate,
    onError: toast.showError,
  });

  function onCustomSubmit(e: FormEvent) {
    e.preventDefault();
    const raw = custom.trim();
    if (!raw) return;
    const ml = Number(raw);
    if (!Number.isFinite(ml)) {
      toast.warn(`"${raw}" is not a number.`);
      return;
    }
    if (ml <= 0) {
      toast.warn('Enter a positive number of millilitres.');
      return;
    }
    addWater.mutate(Math.round(ml), { onSuccess: () => setCustom('') });
  }

  const fraction = water.targetMl > 0 ? water.totalMl / water.targetMl : 0;

  return (
    <section className="card stack-10">
      <div className="card-title">WATER</div>
      <div className="medium-number">
        {water.totalMl} / {water.targetMl} ml
      </div>
      <ProgressBar value={fraction} variant="water" height={10} />

      <div className="row">
        <button
          type="button"
          className="btn"
          onClick={() => addWater.mutate(250)}
          disabled={addWater.isPending}
        >
          +250 ml
        </button>
        <button
          type="button"
          className="btn"
          onClick={() => addWater.mutate(500)}
          disabled={addWater.isPending}
        >
          +500 ml
        </button>
      </div>

      <form className="row" onSubmit={onCustomSubmit}>
        <label className="muted nowrap" htmlFor="water-custom">
          Custom:
        </label>
        <input
          id="water-custom"
          type="number"
          min={1}
          step={10}
          placeholder="ml"
          style={{ width: 90 }}
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
        />
        <button type="submit" className="btn" disabled={addWater.isPending}>
          Add
        </button>
      </form>

      <div className="card-title">Today's intake (click to remove)</div>
      <div className="chips">
        {water.entries.length === 0 ? (
          <span className="muted">(nothing yet)</span>
        ) : (
          water.entries.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className="water-chip"
              title="Remove this entry"
              onClick={() => removeWater.mutate(entry.id)}
              disabled={removeWater.isPending}
            >
              {entry.ml} ml ✕
            </button>
          ))
        )}
      </div>
    </section>
  );
}
