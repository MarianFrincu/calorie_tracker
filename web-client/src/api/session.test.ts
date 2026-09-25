import { describe, expect, it } from 'vitest';

import { expiryOf } from './session';

function jwtWithPayload(payload: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'RS256' })}.${b64(payload)}.sig`;
}

describe('session expiry', () => {
  it("uses the token's own exp claim", () => {
    const exp = 1_900_000_000;
    expect(expiryOf(jwtWithPayload({ sub: 'u', exp }))).toBe(exp * 1000);
  });

  it('falls back to 30 minutes for unreadable tokens', () => {
    const now = 1_000_000;
    expect(expiryOf('not-a-jwt', now)).toBe(now + 30 * 60 * 1000);
    expect(expiryOf(jwtWithPayload({ sub: 'u' }), now)).toBe(now + 30 * 60 * 1000);
  });
});
