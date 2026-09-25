/**
 * Rule-based food search with grouped AND/OR expressions, nested up to
 * MAX_NESTING deep. The engine runs in-memory over a pool of foods fetched
 * once when the tab opens. Port of AdvancedSearchView.java.
 */

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Ingredient } from '../../api/types';
import { DataTable, type Column } from '../../components/DataTable';
import { fmt } from '../../lib/format';
import { RuleEditDialog } from './RuleEditDialog';
import {
  BOOL_OP_LABEL,
  FETCH_LIMIT,
  MAX_NESTING,
  addChild,
  countRules,
  defaultRule,
  evaluate,
  newGroup,
  removeNode,
  rootGroup,
  summarise,
  updateNode,
  type BoolOp,
  type Group,
  type Rule,
  type RuleNode,
} from './rules';

const resultColumns: Column<Ingredient>[] = [
  { key: 'name', header: 'Name', render: (i) => i.name },
  { key: 'brand', header: 'Brand', render: (i) => i.brand ?? '' },
  { key: 'kcal', header: 'kcal', numeric: true, render: (i) => i.kcalPer100g },
  { key: 'p', header: 'P', numeric: true, render: (i) => fmt(i.proteinPer100g) },
  { key: 'c', header: 'C', numeric: true, render: (i) => fmt(i.carbsPer100g) },
  { key: 'f', header: 'F', numeric: true, render: (i) => fmt(i.fatPer100g) },
  { key: 'fib', header: 'Fib', numeric: true, render: (i) => fmt(i.fiberPer100g) },
];

/** One group box: connector picker, its children, and add/delete controls. */
function GroupEditor({
  group,
  depth,
  onChange,
  onDelete,
  onEditRule,
}: {
  group: Group;
  depth: number;
  onChange: (updater: (tree: Group) => Group) => void;
  onDelete: (() => void) | null;
  onEditRule: (rule: Rule) => void;
}) {
  return (
    <div className="rule-group">
      <div className="row wrap">
        <span className="muted nowrap">Combine:</span>
        <select
          className="field-inline"
          value={group.connector}
          onChange={(e) =>
            onChange((tree) =>
              updateNode(tree, group.id, (node) =>
                node.type === 'group' ? { ...node, connector: e.target.value as BoolOp } : node,
              ),
            )
          }
          aria-label="Combine rules with"
        >
          {(['AND', 'OR'] as BoolOp[]).map((op) => (
            <option key={op} value={op}>
              {BOOL_OP_LABEL[op]}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="btn"
          onClick={() => onChange((tree) => addChild(tree, group.id, defaultRule()))}
        >
          + Rule
        </button>
        <button
          type="button"
          className="btn"
          disabled={depth >= MAX_NESTING}
          title={depth >= MAX_NESTING ? `Nesting is limited to ${MAX_NESTING} levels` : undefined}
          onClick={() => onChange((tree) => addChild(tree, group.id, newGroup()))}
        >
          + Group
        </button>
        {onDelete ? (
          <>
            <span className="spacer" />
            <button type="button" className="btn btn-danger" onClick={onDelete}>
              Delete group
            </button>
          </>
        ) : null}
      </div>

      <div className="col" style={{ gap: 4 }}>
        {group.children.map((child: RuleNode) =>
          child.type === 'rule' ? (
            <div className="rule-row" key={child.id}>
              <span className="rule-summary">{summarise(child)}</span>
              <button type="button" className="btn" onClick={() => onEditRule(child)}>
                Edit
              </button>
              <button
                type="button"
                className="btn btn-danger"
                aria-label="Delete rule"
                onClick={() => onChange((tree) => removeNode(tree, child.id))}
              >
                ✕
              </button>
            </div>
          ) : (
            <GroupEditor
              key={child.id}
              group={child}
              depth={depth + 1}
              onChange={onChange}
              onDelete={() => onChange((tree) => removeNode(tree, child.id))}
              onEditRule={onEditRule}
            />
          ),
        )}
      </div>
    </div>
  );
}

export function AdvancedSearch() {
  const [tree, setTree] = useState<Group>(rootGroup);
  const [editing, setEditing] = useState<Rule | null>(null);
  const [results, setResults] = useState<Ingredient[] | null>(null);
  const [status, setStatus] = useState('Build rules and click Apply.');

  const objectiveQuery = useQuery({
    queryKey: ['objective'],
    queryFn: ({ signal }) => api.getObjective(signal),
  });

  // One bulk fetch when the tab opens; Apply then filters in memory so it feels
  // instant regardless of how many rules the user stacks up.
  const poolQuery = useQuery({
    queryKey: ['ingredients', 'pool', FETCH_LIMIT],
    queryFn: ({ signal }) => api.searchAllIngredients('', 0, FETCH_LIMIT, signal),
  });

  const ruleCount = useMemo(() => countRules(tree), [tree]);

  function apply() {
    const pool = poolQuery.data;
    if (!pool) {
      setStatus('Foods are still loading — try again in a second.');
      return;
    }
    if (ruleCount === 0) {
      setStatus('Add at least one rule first.');
      return;
    }
    const objective = objectiveQuery.data ?? null;
    const hits = pool.filter((food) => evaluate(tree, food, objective));
    setResults(hits);
    setStatus(`${hits.length} of ${pool.length} foods match the rules.`);
  }

  function clear() {
    setTree(newGroup());
    setResults(null);
    setStatus('Cleared. Click + Rule to add one.');
  }

  return (
    <div className="two-pane" style={{ gridTemplateColumns: 'minmax(340px, 560px) minmax(0, 1fr)' }}>
      <section className="card stack-10">
        <h2 className="section-title">Rules</h2>
        <p className="muted">
          Click + Rule and a dialog opens with comfortable controls for the comparison. Each rule is
          shown as a one-line summary you can click Edit on later. Use + Group to nest with its own
          AND/OR.
        </p>

        <GroupEditor
          group={tree}
          depth={0}
          onChange={(updater) => setTree(updater)}
          onDelete={null}
          onEditRule={setEditing}
        />

        <div className="row">
          <button type="button" className="btn btn-primary" onClick={apply}>
            Apply
          </button>
          <button type="button" className="btn" onClick={clear}>
            Clear
          </button>
        </div>

        <p className="muted">
          {poolQuery.isPending
            ? 'Loading foods…'
            : poolQuery.isError
              ? "Couldn't load the food pool."
              : `${poolQuery.data?.length ?? 0} foods loaded. ${status}`}
        </p>
      </section>

      <section className="card stack-10">
        <h2 className="section-title">Matching foods (per 100 g)</h2>
        <DataTable
          columns={resultColumns}
          rows={results ?? []}
          rowKey={(i) => i.id}
          placeholder="Apply at least one rule to see results."
          maxHeight={560}
        />
      </section>

      {editing ? (
        <RuleEditDialog
          initial={editing}
          onCancel={() => setEditing(null)}
          onSubmit={(updated) => {
            setTree((tree) => updateNode(tree, updated.id, () => updated));
            setEditing(null);
          }}
        />
      ) : null}
    </div>
  );
}
