/**
 * Rule model + evaluation engine for the Advanced search tab.
 *
 * A rule compares two sides, each of which is `coefficient × (plain number |
 * food field | daily goal)`. Rules live in groups; each group combines its
 * children with AND or OR and groups nest up to MAX_NESTING deep.
 *
 * The desktop version tied this model to the JavaFX widget tree. Here it is
 * plain data so the React components stay presentational and the engine is
 * independently testable.
 */

import type { Ingredient, Objective } from '../../api/types';

export const MAX_NESTING = 4;
export const FETCH_LIMIT = 500;

export type FoodField = 'KCAL' | 'PROTEIN' | 'CARBS' | 'FAT' | 'FIBER';
export type GoalRef = 'CALORIE' | 'PROTEIN' | 'CARBS' | 'FAT' | 'FIBER' | 'WATER';
export type SideKind = 'PLAIN' | 'FOOD' | 'GOAL';
export type Op = 'GT' | 'GE' | 'LT' | 'LE' | 'EQ';
export type BoolOp = 'AND' | 'OR';

export const FOOD_FIELDS: FoodField[] = ['KCAL', 'PROTEIN', 'CARBS', 'FAT', 'FIBER'];
export const GOAL_REFS: GoalRef[] = ['CALORIE', 'PROTEIN', 'CARBS', 'FAT', 'FIBER', 'WATER'];
export const SIDE_KINDS: SideKind[] = ['PLAIN', 'FOOD', 'GOAL'];
export const OPS: Op[] = ['GT', 'GE', 'LT', 'LE', 'EQ'];

export const FOOD_FIELD_LABEL: Record<FoodField, string> = {
  KCAL: 'Calories',
  PROTEIN: 'Protein',
  CARBS: 'Carbs',
  FAT: 'Fat',
  FIBER: 'Fiber',
};

export const GOAL_REF_LABEL: Record<GoalRef, string> = {
  CALORIE: 'Daily calorie target',
  PROTEIN: 'Daily protein target',
  CARBS: 'Daily carbs target',
  FAT: 'Daily fat target',
  FIBER: 'Daily fiber target',
  WATER: 'Daily water target',
};

export const SIDE_KIND_LABEL: Record<SideKind, string> = {
  PLAIN: 'Plain number',
  FOOD: 'Food field',
  GOAL: 'Daily goal',
};

export const OP_SYMBOL: Record<Op, string> = {
  GT: '>',
  GE: '>=',
  LT: '<',
  LE: '<=',
  EQ: '=',
};

export const BOOL_OP_LABEL: Record<BoolOp, string> = {
  AND: 'Match ALL (AND)',
  OR: 'Match ANY (OR)',
};

const FOOD_EXTRACT: Record<FoodField, (i: Ingredient) => number> = {
  KCAL: (i) => i.kcalPer100g,
  PROTEIN: (i) => i.proteinPer100g,
  CARBS: (i) => i.carbsPer100g,
  FAT: (i) => i.fatPer100g,
  FIBER: (i) => i.fiberPer100g,
};

const GOAL_EXTRACT: Record<GoalRef, (o: Objective) => number | null> = {
  CALORIE: (o) => o.dailyCalorieTarget,
  PROTEIN: (o) => o.dailyProteinTargetG,
  CARBS: (o) => o.dailyCarbsTargetG,
  FAT: (o) => o.dailyFatTargetG,
  FIBER: (o) => o.dailyFiberTargetG,
  WATER: (o) => o.dailyWaterTargetMl,
};

export interface Side {
  coef: number;
  kind: SideKind;
  foodField: FoodField;
  goalRef: GoalRef;
}

export interface Rule {
  id: string;
  type: 'rule';
  lhs: Side;
  op: Op;
  rhs: Side;
}

export interface Group {
  id: string;
  type: 'group';
  connector: BoolOp;
  children: RuleNode[];
}

export type RuleNode = Rule | Group;

let idCounter = 0;
function nextId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${idCounter}`;
}

export function defaultRule(): Rule {
  return {
    id: nextId('rule'),
    type: 'rule',
    lhs: { coef: 1, kind: 'FOOD', foodField: 'PROTEIN', goalRef: 'PROTEIN' },
    op: 'GT',
    rhs: { coef: 20, kind: 'PLAIN', foodField: 'CARBS', goalRef: 'CALORIE' },
  };
}

export function newGroup(): Group {
  return { id: nextId('group'), type: 'group', connector: 'AND', children: [] };
}

export function rootGroup(): Group {
  return { ...newGroup(), children: [defaultRule()] };
}

// ---------------- evaluation ----------------

function evaluateSide(side: Side, food: Ingredient, objective: Objective | null): number {
  switch (side.kind) {
    case 'PLAIN':
      return side.coef;
    case 'FOOD':
      return side.coef * FOOD_EXTRACT[side.foodField](food);
    case 'GOAL': {
      const target = objective ? GOAL_EXTRACT[side.goalRef](objective) : null;
      // No target set means "don't constrain on this" — signalled as NaN and
      // treated as a pass by evaluateRule.
      return target == null ? Number.NaN : side.coef * target;
    }
  }
}

function compare(op: Op, left: number, right: number): boolean {
  switch (op) {
    case 'GT':
      return left > right;
    case 'GE':
      return left >= right;
    case 'LT':
      return left < right;
    case 'LE':
      return left <= right;
    case 'EQ':
      return Math.abs(left - right) < 1e-9;
  }
}

export function evaluate(
  node: RuleNode,
  food: Ingredient,
  objective: Objective | null,
): boolean {
  if (node.type === 'rule') {
    const left = evaluateSide(node.lhs, food, objective);
    const right = evaluateSide(node.rhs, food, objective);
    // A missing goal target can't decide anything, so the rule passes through.
    if (Number.isNaN(left) || Number.isNaN(right)) return true;
    return compare(node.op, left, right);
  }
  // An empty group is "no constraint": vacuously true for AND, false for OR.
  if (node.children.length === 0) return node.connector === 'AND';
  return node.connector === 'AND'
    ? node.children.every((child) => evaluate(child, food, objective))
    : node.children.some((child) => evaluate(child, food, objective));
}

export function countRules(node: RuleNode): number {
  return node.type === 'rule'
    ? 1
    : node.children.reduce((sum, child) => sum + countRules(child), 0);
}

// ---------------- rendering helpers ----------------

function fmtCoef(value: number): string {
  if (Number.isInteger(value)) return String(value);
  return String(Number(value.toFixed(2)));
}

function coefPrefix(coef: number): string {
  return Math.abs(coef - 1) < 1e-9 ? '' : `${fmtCoef(coef)} × `;
}

export function describeSide(side: Side): string {
  switch (side.kind) {
    case 'PLAIN':
      return fmtCoef(side.coef);
    case 'FOOD':
      return coefPrefix(side.coef) + FOOD_FIELD_LABEL[side.foodField];
    case 'GOAL':
      return coefPrefix(side.coef) + GOAL_REF_LABEL[side.goalRef];
  }
}

export function summarise(rule: Rule): string {
  return `${describeSide(rule.lhs)}  ${OP_SYMBOL[rule.op]}  ${describeSide(rule.rhs)}`;
}

// ---------------- immutable tree edits ----------------

export function updateNode(tree: Group, id: string, updater: (node: RuleNode) => RuleNode): Group {
  const walk = (node: RuleNode): RuleNode => {
    if (node.id === id) return updater(node);
    if (node.type === 'group') return { ...node, children: node.children.map(walk) };
    return node;
  };
  return walk(tree) as Group;
}

export function addChild(tree: Group, groupId: string, child: RuleNode): Group {
  return updateNode(tree, groupId, (node) =>
    node.type === 'group' ? { ...node, children: [...node.children, child] } : node,
  );
}

export function removeNode(tree: Group, id: string): Group {
  const walk = (node: Group): Group => ({
    ...node,
    children: node.children
      .filter((child) => child.id !== id)
      .map((child) => (child.type === 'group' ? walk(child) : child)),
  });
  return walk(tree);
}
