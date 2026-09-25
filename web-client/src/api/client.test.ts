// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const refreshSession = vi.fn();
const clearSession = vi.fn();
let currentToken: string | null = 'token-1';

vi.mock('./session', () => ({
  getToken: () => currentToken,
  refreshSession: () => refreshSession(),
  clearSession: () => clearSession(),
}));

const { api, ApiError } = await import('./client');

function respond(status: number, body = ''): Response {
  return new Response(status === 204 ? null : body, { status, headers: { 'Content-Type': 'application/json' } });
}

describe('API client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    currentToken = 'token-1';
    fetchMock.mockReset();
    refreshSession.mockReset();
    clearSession.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('sends the bearer token, the time zone and JSON bodies', async () => {
    fetchMock.mockResolvedValueOnce(respond(201, '{"ml":250,"id":1}'));
    await api.addWater('2026-09-24', 250);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/water');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer token-1');
    expect(init.headers['X-Time-Zone']).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ date: '2026-09-24', ml: 250 });
    expect(init.credentials).toBe('omit');
  });

  it('builds query strings and skips empty values', async () => {
    fetchMock.mockResolvedValueOnce(respond(200, '[]'));
    await api.searchAllIngredients('egg & rice', 0, 20);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/ingredients?q=egg+%26+rice&page=0&size=20&scope=all');
    fetchMock.mockResolvedValueOnce(respond(200, '[]'));
    await api.listMyRecipes('', 1, 10);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/recipes?page=1&size=10&scope=mine');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    fetchMock.mockResolvedValueOnce(respond(401)).mockResolvedValueOnce(respond(200, '{"bmr":1780}'));
    refreshSession.mockImplementationOnce(async () => {
      currentToken = 'token-2';
      return true;
    });
    const profile = await api.getProfile();
    expect(profile.bmr).toBe(1780);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer token-2');
    expect(clearSession).not.toHaveBeenCalled();
  });

  it('signs out when the refresh fails', async () => {
    fetchMock.mockResolvedValue(respond(401));
    refreshSession.mockResolvedValueOnce(false);
    await expect(api.getProfile()).rejects.toMatchObject({ status: 401 });
    expect(clearSession).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('replaces codes and proxy error pages with a plain sentence', async () => {
    fetchMock.mockResolvedValueOnce(respond(502, '<html><body>502 Bad Gateway</body></html>'));
    const error = await api.getProfile().catch((e: unknown) => e);
    expect((error as InstanceType<typeof ApiError>).message).toBe(
      'Something went wrong on our side. Please try again in a moment.',
    );
    expect((error as InstanceType<typeof ApiError>).message).not.toMatch(/502|html/i);
  });

  it("surfaces the server's error message", async () => {
    fetchMock.mockResolvedValueOnce(
      respond(409, '{"status":409,"error":"Conflict","message":"Cannot delete a public ingredient."}'),
    );
    const error = await api.deleteIngredient(1).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as InstanceType<typeof ApiError>).message).toBe('Cannot delete a public ingredient.');
    expect((error as InstanceType<typeof ApiError>).isAuthFailure).toBe(false);
  });

  it('treats 204 as no content', async () => {
    fetchMock.mockResolvedValueOnce(respond(204));
    await expect(api.deleteProfile()).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE');
  });

  it('turns network failures and timeouts into readable errors', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(api.getProfile()).rejects.toMatchObject({ status: 0, message: expect.stringContaining('Cannot reach') });

    vi.useFakeTimers();
    fetchMock.mockImplementationOnce(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) =>
          init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))),
        ),
    );
    const pending = api.getProfile().catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(20_001);
    expect(await pending).toMatchObject({ status: 0, message: expect.stringContaining('too long') });
  });
});
