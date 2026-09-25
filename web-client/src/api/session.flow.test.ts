// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const refresh = vi.fn();
const revoke = vi.fn();
vi.mock('./cognito', () => ({ refresh: (...a: unknown[]) => refresh(...a), revoke: (...a: unknown[]) => revoke(...a) }));
vi.mock('./config', () => ({ AUTH_MODE: 'cognito', COGNITO_USER_POOL_ID: 'eu-central-1_Pool1', COGNITO_REGION: 'eu-central-1', COGNITO_CLIENT_ID: 'client-1' }));

const session = await import('./session');

function jwt(expSecondsFromNow: number): string {
  const payload = btoa(JSON.stringify({ sub: 'u', exp: Math.floor(Date.now() / 1000) + expSecondsFromNow }))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `h.${payload}.s`;
}

describe('session store', () => {
  beforeEach(() => {
    session.clearSession();
    sessionStorage.clear();
    refresh.mockReset();
    revoke.mockReset().mockResolvedValue(undefined);
  });

  it('persists only the access token, never the refresh token', () => {
    const access = jwt(3600);
    session.setSession({ accessToken: access, refreshToken: 'refresh-secret' });
    expect(session.getToken()).toBe(access);
    const stored = sessionStorage.getItem('ct.session') ?? '';
    expect(stored).toContain(access);
    expect(stored).not.toContain('refresh-secret');
  });

  it('restores a still-valid token after a reload and drops an expired one', () => {
    const access = jwt(3600);
    sessionStorage.setItem('ct.session', JSON.stringify({ token: access, expiresAt: Date.now() + 60_000 }));
    expect(session.restoreSession()).toBe(access);

    session.clearSession();
    sessionStorage.setItem('ct.session', JSON.stringify({ token: access, expiresAt: Date.now() - 1 }));
    expect(session.restoreSession()).toBeNull();
    expect(sessionStorage.getItem('ct.session')).toBeNull();
  });

  it('refreshes with the refresh token, sharing one request between callers', async () => {
    session.setSession({ accessToken: jwt(3600), refreshToken: 'refresh-1' });
    const fresh = jwt(3600);
    refresh.mockResolvedValueOnce({ accessToken: fresh });
    const [a, b] = await Promise.all([session.refreshSession(), session.refreshSession()]);
    expect(a && b).toBe(true);
    expect(refresh).toHaveBeenCalledOnce();
    expect(refresh).toHaveBeenCalledWith('eu-central-1', 'client-1', 'refresh-1');
    expect(session.getToken()).toBe(fresh);
  });

  it('signs out when the refresh is rejected', async () => {
    session.setSession({ accessToken: jwt(3600), refreshToken: 'revoked' });
    refresh.mockRejectedValueOnce(new Error('NotAuthorizedException'));
    await expect(session.refreshSession()).resolves.toBe(false);
    expect(session.getToken()).toBeNull();
  });

  it('revokes the refresh token on an explicit sign-out and notifies listeners', () => {
    const seen: (string | null)[] = [];
    const unsubscribe = session.onSessionChange((t) => seen.push(t));
    session.setSession({ accessToken: jwt(3600), refreshToken: 'refresh-9' });
    session.clearSession({ revoke: true });
    unsubscribe();
    expect(revoke).toHaveBeenCalledWith('eu-central-1', 'client-1', 'refresh-9');
    expect(seen.at(-1)).toBeNull();
    expect(session.getToken()).toBeNull();
  });

  it('without a refresh token there is nothing to refresh', async () => {
    session.setSession({ accessToken: jwt(3600) });
    await expect(session.refreshSession()).resolves.toBe(false);
    expect(refresh).not.toHaveBeenCalled();
  });
});
