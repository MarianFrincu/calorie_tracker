/**
 * Main day view: calendar nav, four meal panels, summary card, water card.
 * Port of DayView.java.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../api/client';
import { MEALS, type AddDiaryRequest, type DiaryEntry, type IsoDate, type Meal } from '../api/types';
import { AddFoodDialog } from '../dialogs/AddFoodDialog';
import { MoveCopyDialog } from '../dialogs/MoveCopyDialog';
import { useToast } from '../components/Toast';
import { Loading, MacroTags, TitledPane } from '../components/ui';
import { addDays, prettyMeal, today } from '../lib/format';
import { SummaryCard } from './day/SummaryCard';
import { WaterCard } from './day/WaterCard';

type PendingAdd = { meal: Meal } | null;
type PendingMove = { entry: DiaryEntry; mode: 'move' | 'copy' } | null;

export function DayView() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [date, setDate] = useState<IsoDate>(today());
  const [collapsed, setCollapsed] = useState<Partial<Record<Meal, boolean>>>({});
  const [pendingAdd, setPendingAdd] = useState<PendingAdd>(null);
  const [pendingMove, setPendingMove] = useState<PendingMove>(null);
  const [openMenu, setOpenMenu] = useState<number | null>(null);

  const summaryQuery = useQuery({
    queryKey: ['summary', date],
    queryFn: ({ signal }) => api.getDaySummary(date, signal),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['summary'] });
    void queryClient.invalidateQueries({ queryKey: ['report'] });
  };

  const addEntry = useMutation({
    mutationFn: (request: AddDiaryRequest) => api.addDiary(request),
    onSuccess: () => {
      setPendingAdd(null);
      refresh();
    },
    onError: toast.showError,
  });

  const deleteEntry = useMutation({
    mutationFn: (id: number) => api.deleteDiary(id),
    onSuccess: refresh,
    onError: toast.showError,
  });

  const moveEntry = useMutation({
    mutationFn: ({
      id,
      target,
      mode,
    }: {
      id: number;
      target: { date: IsoDate; meal: Meal };
      mode: 'move' | 'copy';
    }) =>
      mode === 'move'
        ? api.moveDiary(id, target.date, target.meal)
        : api.copyDiary(id, target.date, target.meal),
    onSuccess: () => {
      setPendingMove(null);
      refresh();
    },
    onError: toast.showError,
  });

  const summary = summaryQuery.data;

  return (
    <>
      <div className="toolbar">
        <button type="button" className="btn" onClick={() => setDate(addDays(date, -1))}>
          ‹ Prev
        </button>
        <input
          type="date"
          className="field-inline"
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
          aria-label="Diary date"
        />
        <button type="button" className="btn" onClick={() => setDate(addDays(date, 1))}>
          Next ›
        </button>
        <button type="button" className="btn btn-primary" onClick={() => setDate(today())}>
          Today
        </button>
        {summaryQuery.isFetching ? <span className="muted">Refreshing…</span> : null}
      </div>

      <div className="view-scroll">
        {summaryQuery.isPending ? (
          <Loading what="your day" />
        ) : summaryQuery.isError ? (
          <div className="pad-20">
            <div className="inline-hint">
              Couldn't load this day. {(summaryQuery.error as Error).message}
            </div>
          </div>
        ) : summary ? (
          <div className="split">
            <div className="stack-12">
              {MEALS.map((meal) => {
                const block = summary.byMeal[meal] ?? {
                  kcal: 0,
                  protein: 0,
                  carbs: 0,
                  fat: 0,
                  fiber: 0,
                  entries: [],
                };
                const isCollapsed = collapsed[meal] ?? false;
                return (
                  <TitledPane
                    key={meal}
                    expanded={!isCollapsed}
                    onToggle={() => setCollapsed((c) => ({ ...c, [meal]: !isCollapsed }))}
                    title={
                      <span>
                        {prettyMeal(meal)} <span className="muted"> — {block.kcal} kcal</span>
                      </span>
                    }
                  >
                    {block.entries.length === 0 ? (
                      <span className="muted">nothing logged yet</span>
                    ) : (
                      block.entries.map((entry) => (
                        <div className="entry-row" key={entry.id}>
                          <div className="col grow" style={{ gap: 2 }}>
                            <span className="entry-name">{entry.name}</span>
                            {entry.amount ? (
                              <span className="entry-meta">{entry.amount}</span>
                            ) : null}
                          </div>
                          <div className="col" style={{ gap: 4, alignItems: 'flex-end' }}>
                            <span className="entry-kcal">{entry.kcal} kcal</span>
                            <MacroTags
                              protein={entry.protein}
                              carbs={entry.carbs}
                              fat={entry.fat}
                              fiber={entry.fiber}
                            />
                          </div>
                          <div style={{ position: 'relative' }}>
                            <button
                              type="button"
                              className="entry-actions"
                              aria-label={`Actions for ${entry.name}`}
                              aria-expanded={openMenu === entry.id}
                              onClick={() =>
                                setOpenMenu((current) => (current === entry.id ? null : entry.id))
                              }
                            >
                              ⋯
                            </button>
                            {openMenu === entry.id ? (
                              <EntryMenu
                                onClose={() => setOpenMenu(null)}
                                onMove={() => {
                                  setOpenMenu(null);
                                  setPendingMove({ entry, mode: 'move' });
                                }}
                                onCopy={() => {
                                  setOpenMenu(null);
                                  setPendingMove({ entry, mode: 'copy' });
                                }}
                                onDelete={() => {
                                  setOpenMenu(null);
                                  deleteEntry.mutate(entry.id);
                                }}
                              />
                            ) : null}
                          </div>
                        </div>
                      ))
                    )}
                    <button
                      type="button"
                      className="btn-add"
                      onClick={() => setPendingAdd({ meal })}
                    >
                      + Add to {prettyMeal(meal)}
                    </button>
                  </TitledPane>
                );
              })}
            </div>

            <div className="stack-12">
              <SummaryCard summary={summary} />
              <WaterCard water={summary.water} date={date} />
            </div>
          </div>
        ) : null}
      </div>

      {pendingAdd ? (
        <AddFoodDialog
          date={date}
          meal={pendingAdd.meal}
          busy={addEntry.isPending}
          onCancel={() => setPendingAdd(null)}
          onSubmit={(request) => addEntry.mutate(request)}
        />
      ) : null}

      {pendingMove ? (
        <MoveCopyDialog
          title={pendingMove.mode === 'move' ? 'Move entry' : 'Copy entry'}
          currentDate={date}
          currentMeal={pendingMove.entry.meal}
          busy={moveEntry.isPending}
          onCancel={() => setPendingMove(null)}
          onSubmit={(target) =>
            moveEntry.mutate({ id: pendingMove.entry.id, target, mode: pendingMove.mode })
          }
        />
      ) : null}
    </>
  );
}

/** The `⋯` popup on a diary row: Move / Copy / Delete. */
function EntryMenu({
  onClose,
  onMove,
  onCopy,
  onDelete,
}: {
  onClose: () => void;
  onMove: () => void;
  onCopy: () => void;
  onDelete: () => void;
}) {
  return (
    <>
      {/* Full-screen catcher so a click anywhere else dismisses the menu. */}
      <div
        style={{ position: 'fixed', inset: 0, zIndex: 10 }}
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="menu"
        style={{
          position: 'absolute',
          right: 0,
          top: '100%',
          marginTop: 4,
          zIndex: 11,
          background: 'var(--surface)',
          borderRadius: 'var(--r-sm)',
          boxShadow: 'var(--shadow-pop)',
          padding: 4,
          minWidth: 220,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <MenuItem onClick={onMove}>Move to other meal / day…</MenuItem>
        <MenuItem onClick={onCopy}>Copy to other meal / day…</MenuItem>
        <div style={{ height: 1, background: 'var(--line)', margin: '4px 0' }} />
        <MenuItem onClick={onDelete} danger>
          Delete
        </MenuItem>
      </div>
    </>
  );
}

function MenuItem({
  children,
  onClick,
  danger,
}: {
  children: React.ReactNode;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      style={{
        background: 'transparent',
        border: 0,
        textAlign: 'left',
        padding: '8px 10px',
        borderRadius: 'var(--r-sm)',
        cursor: 'pointer',
        fontSize: 13,
        color: danger ? 'var(--danger-strong)' : 'var(--ink-2)',
      }}
      onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--surface-muted)')}
      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
    >
      {children}
    </button>
  );
}
