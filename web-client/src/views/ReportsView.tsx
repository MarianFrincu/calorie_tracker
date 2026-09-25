/**
 * Daily area chart for Calories / Protein / Carbs / Fat / Fiber / Water, with
 * the day-specific historical target drawn as a dashed red curve.
 *
 * "Weekly" = the calendar week (Mon-Sun) containing the anchor date; "Monthly"
 * = the calendar month. Future days are trimmed so a partial current period
 * still shows the right shape. Port of ReportsView.java.
 */

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { api } from '../api/client';
import type { IsoDate } from '../api/types';
import { Loading } from '../components/ui';
import {
  addDays,
  addMonths,
  axisLabel,
  daysBetween,
  endOfMonth,
  fmt,
  isAfter,
  prettyDate,
  startOfMonth,
  startOfWeek,
  today,
} from '../lib/format';

type Period = 'WEEKLY' | 'MONTHLY';
type Metric = 'CALORIES' | 'PROTEIN' | 'CARBS' | 'FAT' | 'FIBER' | 'WATER';

const METRIC_META: Record<Metric, { label: string; unit: string }> = {
  CALORIES: { label: 'Calories', unit: 'kcal' },
  PROTEIN: { label: 'Protein', unit: 'g' },
  CARBS: { label: 'Carbs', unit: 'g' },
  FAT: { label: 'Fat', unit: 'g' },
  FIBER: { label: 'Fiber', unit: 'g' },
  WATER: { label: 'Water', unit: 'ml' },
};

interface ChartPoint {
  label: string;
  date: IsoDate;
  actual: number;
  target: number | null;
}

/** Calendar bounds for the anchor, clipped so we never query future days. */
function periodBounds(anchor: IsoDate, period: Period): { from: IsoDate; to: IsoDate } {
  const now = today();
  let from: IsoDate;
  let to: IsoDate;
  if (period === 'WEEKLY') {
    from = startOfWeek(anchor);
    to = addDays(from, 6);
  } else {
    from = startOfMonth(anchor);
    to = endOfMonth(anchor);
  }
  if (isAfter(to, now)) to = now;
  if (isAfter(from, to)) from = to;
  return { from, to };
}

export function ReportsView() {
  const [anchor, setAnchor] = useState<IsoDate>(today());
  const [period, setPeriod] = useState<Period>('WEEKLY');
  const [metric, setMetric] = useState<Metric>('CALORIES');

  const { from, to } = useMemo(() => periodBounds(anchor, period), [anchor, period]);

  const nutritionQuery = useQuery({
    queryKey: ['report', 'nutrition', from, to],
    queryFn: ({ signal }) => api.nutritionReport(from, to, signal),
  });
  const waterQuery = useQuery({
    queryKey: ['report', 'water', from, to],
    queryFn: ({ signal }) => api.waterReport(from, to, signal),
  });

  const points = useMemo<ChartPoint[]>(() => {
    if (metric === 'WATER') {
      const rows = waterQuery.data ?? [];
      return rows.map((p, i) => ({
        label: axisLabel(p.date, i > 0 ? rows[i - 1].date : undefined),
        date: p.date,
        actual: p.ml,
        target: p.mlTarget,
      }));
    }
    const rows = nutritionQuery.data ?? [];
    return rows.map((p, i) => {
      const actual =
        metric === 'CALORIES'
          ? p.kcal
          : metric === 'PROTEIN'
            ? p.protein
            : metric === 'CARBS'
              ? p.carbs
              : metric === 'FAT'
                ? p.fat
                : p.fiber;
      const target =
        metric === 'CALORIES'
          ? p.kcalTarget
          : metric === 'PROTEIN'
            ? p.proteinTarget
            : metric === 'CARBS'
              ? p.carbsTarget
              : metric === 'FAT'
                ? p.fatTarget
                : p.fiberTarget;
      return {
        label: axisLabel(p.date, i > 0 ? rows[i - 1].date : undefined),
        date: p.date,
        actual,
        target,
      };
    });
  }, [metric, nutritionQuery.data, waterQuery.data]);

  const stats = useMemo(() => {
    if (points.length === 0) return { mean: null, min: null, max: null, target: null };
    const values = points.map((p) => p.actual);
    const targets = points.map((p) => p.target).filter((t): t is number => t != null);
    return {
      mean: values.reduce((a, b) => a + b, 0) / values.length,
      min: Math.min(...values),
      max: Math.max(...values),
      target: targets.length > 0 ? targets[targets.length - 1] : null,
    };
  }, [points]);

  const meta = METRIC_META[metric];
  const loading = nutritionQuery.isPending || waterQuery.isPending;
  const days = daysBetween(from, to);

  function shift(sign: 1 | -1) {
    const next = period === 'WEEKLY' ? addDays(anchor, sign * 7) : addMonths(anchor, sign);
    setAnchor(isAfter(next, today()) ? today() : next);
  }

  return (
    <>
      <div className="toolbar">
        <span className="muted nowrap">Any date in period:</span>
        <button type="button" className="btn" onClick={() => shift(-1)}>
          ‹ Prev
        </button>
        <input
          type="date"
          className="field-inline"
          value={anchor}
          max={today()}
          onChange={(e) => e.target.value && setAnchor(e.target.value)}
          aria-label="Anchor date"
        />
        <button type="button" className="btn btn-primary" onClick={() => setAnchor(today())}>
          Today
        </button>
        <button type="button" className="btn" onClick={() => shift(1)}>
          Next ›
        </button>

        <label className="muted nowrap" htmlFor="r-period">
          Period:
        </label>
        <select
          id="r-period"
          className="field-inline"
          value={period}
          onChange={(e) => setPeriod(e.target.value as Period)}
        >
          <option value="WEEKLY">Weekly (this calendar week)</option>
          <option value="MONTHLY">Monthly (this calendar month)</option>
        </select>

        <span className="spacer" />

        <label className="muted nowrap" htmlFor="r-metric">
          Metric:
        </label>
        <select
          id="r-metric"
          className="field-inline"
          value={metric}
          onChange={(e) => setMetric(e.target.value as Metric)}
        >
          {(Object.keys(METRIC_META) as Metric[]).map((m) => (
            <option key={m} value={m}>
              {METRIC_META[m].label}
            </option>
          ))}
        </select>
      </div>

      <div className="row" style={{ padding: '6px 16px' }}>
        <span className="muted">
          {from} to {to} ({days} day{days === 1 ? '' : 's'})
        </span>
      </div>

      <div className="view-scroll">
        {loading ? (
          <Loading what="your report" />
        ) : (
          <div className="two-pane" style={{ gridTemplateColumns: 'minmax(0, 1fr) 260px' }}>
            <div className="chart-card">
              {points.length === 0 ? (
                <p className="muted">Nothing logged in this period yet.</p>
              ) : (
                <ResponsiveContainer width="100%" height={420}>
                  <ComposedChart data={points} margin={{ top: 12, right: 16, bottom: 8, left: 4 }}>
                    <defs>
                      <linearGradient id="actualFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#4299e1" stopOpacity={0.35} />
                        <stop offset="100%" stopColor="#4299e1" stopOpacity={0.08} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
                    <XAxis
                      dataKey="label"
                      tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                      stroke="var(--line-strong)"
                    />
                    <YAxis
                      tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                      stroke="var(--line-strong)"
                      label={{
                        value: `${meta.label} (${meta.unit})`,
                        angle: -90,
                        position: 'insideLeft',
                        fill: 'var(--ink-3)',
                        fontSize: 11,
                      }}
                    />
                    <Tooltip content={<ReportTooltip unit={meta.unit} />} />
                    <Legend />
                    {/* Actual: translucent blue area + solid blue monotone line. */}
                    <Area
                      name="Actual"
                      type="monotone"
                      dataKey="actual"
                      stroke="#2b6cb0"
                      strokeWidth={2.2}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      fill="url(#actualFill)"
                      dot={{ r: 4, fill: '#2b6cb0', stroke: '#1a202c', strokeWidth: 1 }}
                      activeDot={{ r: 6 }}
                      isAnimationActive={false}
                    />
                    {/* Target: no fill, dashed red — reads unambiguously as a goal line. */}
                    <Area
                      name="Target"
                      type="monotone"
                      dataKey="target"
                      stroke="#c53030"
                      strokeWidth={2.5}
                      strokeDasharray="8 6"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      fill="transparent"
                      dot={{ r: 3.5, fill: '#c53030', stroke: '#1a202c', strokeWidth: 1 }}
                      activeDot={{ r: 5 }}
                      connectNulls
                      isAnimationActive={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              )}
            </div>

            <section className="card stack-10">
              <div className="card-title">STATS</div>
              <StatRow label="Mean" value={stats.mean} unit={meta.unit} />
              <StatRow label="Min" value={stats.min} unit={meta.unit} />
              <StatRow label="Max" value={stats.max} unit={meta.unit} />
              <StatRow label="Target" value={stats.target} unit={meta.unit} />
            </section>
          </div>
        )}
      </div>
    </>
  );
}

function StatRow({ label, value, unit }: { label: string; value: number | null; unit: string }) {
  return (
    <div className="row">
      <span className="muted">{label}</span>
      <span className="spacer" />
      <span className="medium-number">
        {value == null ? '-' : `${fmt(value)} ${unit}`}
      </span>
    </div>
  );
}

interface TooltipProps {
  active?: boolean;
  payload?: { payload: ChartPoint }[];
  unit: string;
}

function ReportTooltip({ active, payload, unit }: TooltipProps) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="chart-tooltip">
      <div>{prettyDate(point.date)}</div>
      <div>
        Actual: {fmt(point.actual)} {unit}
      </div>
      {point.target != null ? (
        <div>
          Target: {fmt(point.target)} {unit}
        </div>
      ) : null}
    </div>
  );
}
