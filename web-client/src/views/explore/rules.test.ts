import { describe, expect, it } from 'vitest';

import type { Ingredient, Objective } from '../../api/types';
import { addChild, countRules, evaluate, newGroup, removeNode, type Rule } from './rules';

const chicken: Ingredient = {
  id: 1, name: 'Chicken breast', brand: null, kcalPer100g: 165, proteinPer100g: 31,
  carbsPer100g: 0, fatPer100g: 3.6, fiberPer100g: 0, isPublic: true,
};
const rice: Ingredient = {
  id: 2, name: 'Rice', brand: null, kcalPer100g: 130, proteinPer100g: 2.7,
  carbsPer100g: 28, fatPer100g: 0.3, fiberPer100g: 0.4, isPublic: true,
};
const objective = { dailyProteinTargetG: 150 } as Objective;

function rule(partial: Partial<Rule>): Rule {
  return {
    id: Math.random().toString(),
    type: 'rule',
    lhs: { coef: 1, kind: 'FOOD', foodField: 'PROTEIN', goalRef: 'PROTEIN' },
    op: 'GT',
    rhs: { coef: 20, kind: 'PLAIN', foodField: 'CARBS', goalRef: 'CALORIE' },
    ...partial,
  };
}

describe('advanced-search rule engine', () => {
  it('compares a food field with a plain number', () => {
    const highProtein = rule({});
    expect(evaluate(highProtein, chicken, null)).toBe(true);
    expect(evaluate(highProtein, rice, null)).toBe(false);
  });

  it('references daily goals and passes when the goal is unset', () => {
    // protein per 100 g >= 20% of the daily protein target (30 g)
    const vsGoal = rule({ op: 'GE', rhs: { coef: 0.2, kind: 'GOAL', foodField: 'CARBS', goalRef: 'PROTEIN' } });
    expect(evaluate(vsGoal, chicken, objective)).toBe(true);
    expect(evaluate(vsGoal, rice, objective)).toBe(false);
    expect(evaluate(vsGoal, rice, null)).toBe(true);
  });

  it('combines children with AND / OR and treats empty groups sensibly', () => {
    const lowCarb = rule({ lhs: { coef: 1, kind: 'FOOD', foodField: 'CARBS', goalRef: 'PROTEIN' }, op: 'LT',
      rhs: { coef: 5, kind: 'PLAIN', foodField: 'CARBS', goalRef: 'CALORIE' } });
    const and = { ...newGroup(), children: [rule({}), lowCarb] };
    const or = { ...newGroup(), connector: 'OR' as const, children: [rule({}), lowCarb] };
    expect(evaluate(and, chicken, null)).toBe(true);
    expect(evaluate(and, rice, null)).toBe(false);
    expect(evaluate(or, rice, null)).toBe(false);
    expect(evaluate(newGroup(), rice, null)).toBe(true);
    expect(evaluate({ ...newGroup(), connector: 'OR' }, rice, null)).toBe(false);
  });

  it('edits the tree immutably', () => {
    const root = newGroup();
    const r = rule({});
    const withRule = addChild(root, root.id, r);
    expect(countRules(withRule)).toBe(1);
    expect(countRules(root)).toBe(0);
    expect(countRules(removeNode(withRule, r.id))).toBe(0);
  });
});
