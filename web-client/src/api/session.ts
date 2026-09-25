/**
 * Token store. The access token (1 h) lives in memory and is mirrored to
 * sessionStorage so a page refresh keeps you signed in (per tab, gone with
 * it). The refresh token (30 days) stays in memory only: it extends the
 * session silently while the tab is open. The strict CSP is what protects
 * the stored token from injected scripts.
 */

import { AUTH_MODE, COGNITO_CLIENT_ID, COGNITO_REGION } from './config';
import * as cognito from './cognito';

const KEY = 'ct.session';
/** Fallback lifetime when a token has no readable `exp` claim. */
const DEFAULT_LIFETIME_MS = 30 * 60 * 1000;
/** Refresh this long before expiry so in-flight requests never race it. */
const REFRESH_MARGIN_MS = 2 * 60 * 1000;

interface Persisted {
  token: string;
  expiresAt: number;
}

let token: string | null = null;
let refreshToken: string | null = null;
let expiresAt = 0;
let timer: number | undefined;
let refreshing: Promise<boolean> | null = null;

const listeners = new Set<(token: string | null) => void>();

function notify(): void {
  for (const l of listeners) l(token);
}

/** Subscribe to sign-in / sign-out. Returns an unsubscribe function. */
export function onSessionChange(fn: (token: string | null) => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Expiry (epoch ms) from the JWT's `exp` claim, or a conservative default. */
export function expiryOf(jwt: string, now = Date.now()): number {
  try {
    const payload = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(payload)) as { exp?: unknown };
    if (typeof claims.exp === 'number') return claims.exp * 1000;
  } catch {
    /* not a JWT we can read - fall through */
  }
  return now + DEFAULT_LIFETIME_MS;
}

function schedule(): void {
  if (timer !== undefined) window.clearTimeout(timer);
  timer = undefined;
  if (!token) return;
  const untilRefresh = expiresAt - REFRESH_MARGIN_MS - Date.now();
  if (refreshToken) {
    timer = window.setTimeout(() => void refreshSession(), Math.max(0, untilRefresh));
  } else {
    const untilExpiry = expiresAt - Date.now();
    timer = window.setTimeout(clearSession, Math.max(0, untilExpiry));
  }
}

function persist(): void {
  try {
    if (token) sessionStorage.setItem(KEY, JSON.stringify({ token, expiresAt } satisfies Persisted));
    else sessionStorage.removeItem(KEY);
  } catch {
    // Storage unavailable (private mode / blocked): the in-memory token still
    // works for this page load; only refresh-persistence is lost.
  }
}

/** Rehydrate from sessionStorage. Call once at boot, before the first render. */
export function restoreSession(): string | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Persisted;
    if (!parsed?.token || typeof parsed.expiresAt !== 'number' || parsed.expiresAt <= Date.now()) {
      sessionStorage.removeItem(KEY);
      return null;
    }
    token = parsed.token;
    expiresAt = parsed.expiresAt;
    schedule();
    return token;
  } catch {
    return null;
  }
}

export function setSession(tokens: cognito.Tokens): void {
  token = tokens.accessToken;
  if (tokens.refreshToken) refreshToken = tokens.refreshToken;
  expiresAt = expiryOf(tokens.accessToken);
  persist();
  schedule();
  notify();
}

/**
 * Drops the session locally. Pass {@code revoke: true} on an explicit sign-out
 * to also invalidate the refresh token at Cognito (best effort).
 */
export function clearSession(options: { revoke?: boolean } = {}): void {
  const toRevoke = refreshToken;
  token = null;
  refreshToken = null;
  expiresAt = 0;
  schedule();
  persist();
  notify();
  if (options.revoke && toRevoke && AUTH_MODE === 'cognito') {
    void cognito.revoke(COGNITO_REGION, COGNITO_CLIENT_ID, toRevoke).catch(() => {
      /* already invalid, or offline - the local session is gone either way */
    });
  }
}

/**
 * Gets a new access token with the refresh token. Concurrent callers share
 * one request. Resolves false (and signs out) when refreshing is impossible.
 */
export function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing;
  if (!refreshToken || AUTH_MODE !== 'cognito') return Promise.resolve(false);
  const current = refreshToken;
  refreshing = cognito
    .refresh(COGNITO_REGION, COGNITO_CLIENT_ID, current)
    .then((tokens) => {
      setSession(tokens);
      return true;
    })
    .catch(() => {
      clearSession();
      return false;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

export function getToken(): string | null {
  return token;
}
