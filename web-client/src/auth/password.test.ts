import { describe, expect, it } from 'vitest';

import { PASSWORD_RULES, meetsPasswordRules } from './password';

describe('password rules (mirror the Cognito policy)', () => {
  it('accepts a password meeting every rule', () => {
    expect(meetsPasswordRules('Correct-Horse-9')).toBe(true);
  });

  it('names exactly the rule that is missing', () => {
    const missing = (p: string) => PASSWORD_RULES.filter((r) => !r.met(p)).map((r) => r.label);
    expect(missing('short1A')).toEqual(['At least 12 characters']);
    expect(missing('alllowercase12')).toEqual(['An uppercase letter (A-Z)']);
    expect(missing('ALLUPPERCASE12')).toEqual(['A lowercase letter (a-z)']);
    expect(missing('NoDigitsAtAllHere')).toEqual(['A number (0-9)']);
  });
});
