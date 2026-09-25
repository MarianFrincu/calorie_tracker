/**
 * Weight tracker: a smooth line chart over time plus a table to log new
 * entries (one per day — re-saving a date updates it) and remove old ones.
 * Port of WeightView.java.
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { api } from '../api/client';
import type { IsoDate } from '../api/types';
import { DataTable, type Column } from '../components/DataTable';
import { useToast } from '../components/Toast';
import { Loading } from '../components/ui';
import { axisLabel, prettyDate, today } from '../lib/format';
import type { WeightEntry } from '../api/types';

interface Point {
  label: string;
  date: IsoDate;
  weight: number;
}

export function WeightView() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [date, setDate] = useState<IsoDate>(today());
  const [weight, setWeight] = useState('75');
  // Once the user types, the async prefill below must not overwrite them.
  const touched = useRef(false);

  const weightQuery = useQuery({
    queryKey: ['weight'],
    queryFn: ({ signal }) => api.listWeight(signal),
  });

  // Prefill the input with the latest logged weight, as a convenience.
  useEffect(() => {
    const list = weightQuery.data;
    if (!list || list.length === 0 || touched.current) return;
    const latest = list[list.length - 1];
    if (latest.weightKg != null) setWeight(String(latest.weightKg));
  }, [weightQuery.data]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['weight'] });
    void queryClient.invalidateQueries({ queryKey: ['profile'] });
  };

  const save = useMutation({
    mutationFn: async ({ date: d, kg }: { date: IsoDate; kg: number }) => {
      const saved = await api.upsertWeight(d, kg);
      // Only today's measurement patches the profile — older entries are
      // historical and must not move BMR/TDEE.
      if (d === today()) {
        await api.updateProfile({ weightKg: kg });
      }
      return saved;
    },
    onSuccess: invalidate,
    onError: toast.showError,
  });

  const remove = useMutation({
    mutationFn: (id: number) => api.deleteWeight(id),
    onSuccess: invalidate,
    onError: toast.showError,
  });

  const points = useMemo<Point[]>(() => {
    const list = weightQuery.data ?? [];
    return list
      .filter((e): e is WeightEntry & { weightKg: number } => e.weightKg != null)
      .map((e, index, all) => ({
        label: axisLabel(e.date, index > 0 ? all[index - 1].date : undefined),
        date: e.date,
        weight: e.weightKg,
      }));
  }, [weightQuery.data]);

  const columns: Column<WeightEntry>[] = [
    { key: 'date', header: 'Date', render: (w) => w.date },
    {
      key: 'kg',
      header: 'Weight (kg)',
      numeric: true,
      render: (w) => (w.weightKg != null ? w.weightKg.toFixed(1) : '-'),
    },
    {
      key: 'actions',
      header: '',
      render: (w) => (
        <button
          type="button"
          className="btn btn-danger"
          aria-label={`Delete entry for ${w.date}`}
          onClick={() => remove.mutate(w.id)}
          disabled={remove.isPending}
        >
          ✕
        </button>
      ),
    },
  ];

  // Pad the domain a little so the curve never hugs the plot edges.
  const values = points.map((p) => p.weight);
  const domain: [number, number] | undefined =
    values.length > 0
      ? [Math.floor(Math.min(...values) - 1), Math.ceil(Math.max(...values) + 1)]
      : undefined;

  return (
    <>
      <form
        className="toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          const kg = Number(weight.replace(',', '.'));
          if (!Number.isFinite(kg) || kg <= 0) {
            toast.warn('Enter a weight in kilograms.');
            return;
          }
          save.mutate({ date, kg: Math.round(kg * 10) / 10 });
        }}
      >
        <label className="muted nowrap" htmlFor="w-date">
          Date:
        </label>
        <input
          id="w-date"
          type="date"
          className="field-inline"
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
        />
        <label className="muted nowrap" htmlFor="w-kg">
          Weight (kg):
        </label>
        <input
          id="w-kg"
          type="number"
          min={30}
          max={250}
          step={0.1}
          style={{ width: 110 }}
          value={weight}
          onChange={(e) => {
            touched.current = true;
            setWeight(e.target.value);
          }}
        />
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save weight'}
        </button>
      </form>

      <div className="view-scroll">
        {weightQuery.isPending ? (
          <Loading what="your weight log" />
        ) : (
          <div className="two-pane" style={{ gridTemplateColumns: 'minmax(0, 1fr) 380px' }}>
            <div className="chart-card">
              <h2 className="section-title" style={{ marginBottom: 10 }}>
                Weight over time
              </h2>
              {points.length === 0 ? (
                <p className="muted">
                  No weight logged yet. Use the bar above to add today's measurement.
                </p>
              ) : (
                <ResponsiveContainer width="100%" height={380}>
                  <LineChart data={points} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                    <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
                    <XAxis
                      dataKey="label"
                      tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                      stroke="var(--line-strong)"
                    />
                    <YAxis
                      domain={domain}
                      tickFormatter={(v: number) => v.toFixed(1)}
                      tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                      stroke="var(--line-strong)"
                      label={{
                        value: 'kg',
                        angle: -90,
                        position: 'insideLeft',
                        fill: 'var(--ink-3)',
                        fontSize: 11,
                      }}
                    />
                    <Tooltip content={<WeightTooltip />} />
                    <Line
                      type="monotone"
                      dataKey="weight"
                      stroke="var(--accent-deep)"
                      strokeWidth={2.4}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      dot={{ r: 4, fill: 'var(--accent-deep)', stroke: 'var(--ink)', strokeWidth: 1 }}
                      activeDot={{ r: 6 }}
                      isAnimationActive={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>

            <DataTable
              columns={columns}
              rows={weightQuery.data ?? []}
              rowKey={(w) => w.id}
              placeholder="No weight logged yet. Use the bar above to add today's measurement."
              maxHeight={480}
            />
          </div>
        )}
      </div>
    </>
  );
}

interface TooltipPayload {
  payload?: { payload: Point }[];
  active?: boolean;
}

function WeightTooltip({ active, payload }: TooltipPayload) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="chart-tooltip">
      <div>{prettyDate(point.date)}</div>
      <div>{point.weight.toFixed(1)} kg</div>
    </div>
  );
}
