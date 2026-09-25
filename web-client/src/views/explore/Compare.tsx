/**
 * Compare 2-4 foods side by side. Ingredients are compared per 100 g, recipes
 * per serving. Right pane shows a metrics table plus a grouped bar chart.
 * Port of CompareView.java.
 */

import { useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { api } from '../../api/client';
import type { Ingredient, Recipe } from '../../api/types';
import { DataTable, LoadMoreBar, SearchBar, type Column } from '../../components/DataTable';
import { useToast } from '../../components/Toast';
import { usePager } from '../../components/usePager';

const MAX_SLOTS = 4;

/** Categorical series colours — distinguishable and consistent with the theme. */
const SERIES_COLORS = ['#2b6cb0', '#dd6b20', '#38a169', '#805ad5'];

type SourceKind = 'INGREDIENT' | 'RECIPE';

interface CompareItem {
  kind: SourceKind;
  id: number;
  label: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
}

function fromIngredient(i: Ingredient): CompareItem {
  return {
    kind: 'INGREDIENT',
    id: i.id,
    label: `${i.name} (per 100 g)`,
    kcal: i.kcalPer100g,
    protein: i.proteinPer100g,
    carbs: i.carbsPer100g,
    fat: i.fatPer100g,
    fiber: i.fiberPer100g,
  };
}

function fromRecipe(r: Recipe): CompareItem {
  const servings = Math.max(1, r.servings);
  return {
    kind: 'RECIPE',
    id: r.id,
    label: `${r.name} (per serving)`,
    kcal: Math.round(r.totalKcal / servings),
    protein: r.totalProtein / servings,
    carbs: r.totalCarbs / servings,
    fat: r.totalFat / servings,
    fiber: r.totalFiber / servings,
  };
}

const METRIC_ROWS = [
  { key: 'kcal', label: 'Calories (kcal)', decimals: 0 },
  { key: 'protein', label: 'Protein (g)', decimals: 1 },
  { key: 'carbs', label: 'Carbs (g)', decimals: 1 },
  { key: 'fat', label: 'Fat (g)', decimals: 1 },
  { key: 'fiber', label: 'Fiber (g)', decimals: 1 },
] as const;

export function Compare() {
  const toast = useToast();
  const [kind, setKind] = useState<SourceKind>('INGREDIENT');
  const [picked, setPicked] = useState<CompareItem[]>([]);
  const [selected, setSelected] = useState<CompareItem | null>(null);

  const ingredientPager = usePager<Ingredient>((q, page, size, signal) =>
    api.searchAllIngredients(q, page, size, signal),
  );
  const recipePager = usePager<Recipe>((q, page, size, signal) =>
    api.searchAllRecipes(q, page, size, signal),
  );

  const hits: CompareItem[] = useMemo(
    () =>
      kind === 'INGREDIENT'
        ? ingredientPager.items.map(fromIngredient)
        : recipePager.items.map(fromRecipe),
    [kind, ingredientPager.items, recipePager.items],
  );

  const columns: Column<CompareItem>[] = [
    { key: 'label', header: 'Name', render: (c) => c.label },
    { key: 'kcal', header: 'kcal', numeric: true, render: (c) => Math.round(c.kcal) },
  ];

  function add() {
    if (!selected) return;
    if (picked.length >= MAX_SLOTS) {
      toast.warn(`You can compare at most ${MAX_SLOTS} foods.`);
      return;
    }
    // Skip duplicates silently, matching the desktop behaviour.
    if (picked.some((c) => c.kind === selected.kind && c.id === selected.id)) return;
    setPicked((current) => [...current, selected]);
  }

  const chartData = useMemo(
    () =>
      [
        { metric: 'kcal', key: 'kcal' as const },
        { metric: 'Protein', key: 'protein' as const },
        { metric: 'Carbs', key: 'carbs' as const },
        { metric: 'Fat', key: 'fat' as const },
        { metric: 'Fiber', key: 'fiber' as const },
      ].map(({ metric, key }) => {
        const row: Record<string, string | number> = { metric };
        picked.forEach((item, index) => {
          row[`s${index}`] = Number(item[key].toFixed(1));
        });
        return row;
      }),
    [picked],
  );

  return (
    <div className="two-pane">
      <section className="card stack-10">
        <h2 className="section-title">Pick foods to compare</h2>
        <div className="row">
          <label className="muted nowrap" htmlFor="cmp-source">
            Source:
          </label>
          <select
            id="cmp-source"
            className="field-inline"
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as SourceKind);
              setSelected(null);
            }}
          >
            <option value="INGREDIENT">Food</option>
            <option value="RECIPE">Recipe</option>
          </select>
        </div>

        {kind === 'INGREDIENT' ? (
          <SearchBar pager={ingredientPager} placeholder="search (press Enter)" />
        ) : (
          <SearchBar pager={recipePager} placeholder="search (press Enter)" />
        )}

        <DataTable
          columns={columns}
          rows={hits}
          rowKey={(c) => `${c.kind}-${c.id}`}
          selectedKey={selected ? `${selected.kind}-${selected.id}` : null}
          onSelect={setSelected}
          onActivate={add}
          maxHeight={340}
          placeholder="Nothing found — try another search."
        />
        {kind === 'INGREDIENT' ? (
          <LoadMoreBar pager={ingredientPager} />
        ) : (
          <LoadMoreBar pager={recipePager} />
        )}

        <button type="button" className="btn btn-primary" onClick={add} disabled={!selected}>
          Add to comparison
        </button>
      </section>

      <section className="card stack-10">
        <h2 className="section-title">Comparison</h2>

        <div className="chips">
          {picked.length === 0 ? (
            <span className="muted">Nothing picked yet.</span>
          ) : (
            picked.map((item) => (
              <button
                key={`${item.kind}-${item.id}`}
                type="button"
                className="water-chip"
                title="Remove from comparison"
                onClick={() =>
                  setPicked((current) =>
                    current.filter((c) => !(c.kind === item.kind && c.id === item.id)),
                  )
                }
              >
                {item.label} ✕
              </button>
            ))
          )}
        </div>

        <p className="muted">
          Ingredients are compared per 100 g, recipes per serving. Click a chip to remove it.
        </p>

        {picked.length === 0 ? (
          <div className="table-placeholder">Add at least 2 foods to see the comparison.</div>
        ) : (
          <>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th style={{ width: 160 }}>Metric</th>
                    {picked.map((item) => (
                      <th key={`${item.kind}-${item.id}`} className="num">
                        {item.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {METRIC_ROWS.map((row) => (
                    <tr key={row.key}>
                      <td>{row.label}</td>
                      {picked.map((item) => (
                        <td key={`${item.kind}-${item.id}`} className="num">
                          {item[row.key].toFixed(row.decimals)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={chartData} margin={{ top: 12, right: 12, bottom: 8, left: 0 }}>
                <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="metric"
                  tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                  stroke="var(--line-strong)"
                />
                <YAxis
                  tick={{ fill: 'var(--ink-3)', fontSize: 11 }}
                  stroke="var(--line-strong)"
                  label={{
                    value: 'Value',
                    angle: -90,
                    position: 'insideLeft',
                    fill: 'var(--ink-3)',
                    fontSize: 11,
                  }}
                />
                <Tooltip
                  contentStyle={{
                    background: 'var(--ink)',
                    border: 0,
                    borderRadius: 6,
                    color: '#fff',
                    fontSize: 12,
                  }}
                  itemStyle={{ color: '#fff' }}
                  labelStyle={{ color: '#fff' }}
                />
                <Legend />
                {picked.map((item, index) => (
                  <Bar
                    key={`${item.kind}-${item.id}`}
                    dataKey={`s${index}`}
                    name={item.label}
                    fill={SERIES_COLORS[index % SERIES_COLORS.length]}
                    radius={[3, 3, 0, 0]}
                    isAnimationActive={false}
                  />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </>
        )}
      </section>
    </div>
  );
}
