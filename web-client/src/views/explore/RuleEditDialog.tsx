/**
 * Wide modal editor for a single rule: pick a source on each side (plain
 * number / food field / daily goal), the matching field, a coefficient and an
 * operator, with a live preview of the resulting summary.
 * Port of AdvancedSearchView.RuleEditDialog.
 */

import { useState } from 'react';

import { Modal } from '../../components/Modal';
import { NumberInput } from '../../components/NumberInput';
import {
  FOOD_FIELDS,
  FOOD_FIELD_LABEL,
  GOAL_REFS,
  GOAL_REF_LABEL,
  OPS,
  OP_SYMBOL,
  SIDE_KINDS,
  SIDE_KIND_LABEL,
  summarise,
  type FoodField,
  type GoalRef,
  type Op,
  type Rule,
  type Side,
  type SideKind,
} from './rules';

function SideEditor({
  title,
  side,
  onChange,
  /** Food field already used by the other side — excluded to avoid `Protein > Protein`. */
  excludeFoodField,
}: {
  title: string;
  side: Side;
  onChange: (side: Side) => void;
  excludeFoodField: FoodField | null;
}) {
  const allowedFoodFields = FOOD_FIELDS.filter((f) => f !== excludeFoodField);

  return (
    <div className="col grow" style={{ gap: 8, minWidth: 240 }}>
      <div className="card-title">{title}</div>
      <div className="form-grid" style={{ gridTemplateColumns: 'minmax(96px, max-content) 1fr' }}>
        <label>Coefficient ×</label>
        <NumberInput
          value={side.coef}
          allowNegative
          emptyValue={0}
          onChange={(coef) => onChange({ ...side, coef: coef ?? 0 })}
        />

        <label>Source</label>
        <select
          value={side.kind}
          onChange={(e) => onChange({ ...side, kind: e.target.value as SideKind })}
        >
          {SIDE_KINDS.map((k) => (
            <option key={k} value={k}>
              {SIDE_KIND_LABEL[k]}
            </option>
          ))}
        </select>

        {side.kind === 'FOOD' ? (
          <>
            <label>Food field</label>
            <select
              value={allowedFoodFields.includes(side.foodField) ? side.foodField : allowedFoodFields[0]}
              onChange={(e) => onChange({ ...side, foodField: e.target.value as FoodField })}
            >
              {allowedFoodFields.map((f) => (
                <option key={f} value={f}>
                  {FOOD_FIELD_LABEL[f]}
                </option>
              ))}
            </select>
          </>
        ) : null}

        {side.kind === 'GOAL' ? (
          <>
            <label>Goal</label>
            <select
              value={side.goalRef}
              onChange={(e) => onChange({ ...side, goalRef: e.target.value as GoalRef })}
            >
              {GOAL_REFS.map((g) => (
                <option key={g} value={g}>
                  {GOAL_REF_LABEL[g]}
                </option>
              ))}
            </select>
          </>
        ) : null}
      </div>
    </div>
  );
}

interface Props {
  initial: Rule;
  onSubmit: (rule: Rule) => void;
  onCancel: () => void;
}

export function RuleEditDialog({ initial, onSubmit, onCancel }: Props) {
  const [lhs, setLhs] = useState<Side>(initial.lhs);
  const [op, setOp] = useState<Op>(initial.op);
  const [rhs, setRhs] = useState<Side>(initial.rhs);

  const draft: Rule = { ...initial, lhs, op, rhs };

  return (
    <Modal
      title="Edit rule"
      headerText="Build a comparison between two values."
      size="xwide"
      onOk={() => onSubmit(draft)}
      onCancel={onCancel}
    >
      <div className="row wrap" style={{ alignItems: 'flex-start', gap: 20 }}>
        <SideEditor
          title="LEFT SIDE"
          side={lhs}
          onChange={setLhs}
          excludeFoodField={rhs.kind === 'FOOD' ? rhs.foodField : null}
        />

        <div className="col" style={{ gap: 8, minWidth: 240 }}>
          <div className="card-title">OPERATOR</div>
          <div className="row" style={{ gap: 4 }}>
            {OPS.map((candidate) => (
              <button
                key={candidate}
                type="button"
                className={`op-toggle ${op === candidate ? 'selected' : ''}`}
                aria-pressed={op === candidate}
                onClick={() => setOp(candidate)}
              >
                {OP_SYMBOL[candidate]}
              </button>
            ))}
          </div>
        </div>

        <SideEditor
          title="RIGHT SIDE"
          side={rhs}
          onChange={setRhs}
          excludeFoodField={lhs.kind === 'FOOD' ? lhs.foodField : null}
        />
      </div>

      <div className="preview-row">
        <span className="muted">Preview:</span>
        <span className="medium-number">{summarise(draft)}</span>
      </div>
    </Modal>
  );
}
