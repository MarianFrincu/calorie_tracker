/**
 * REST client for the Calorie Tracker gateway.
 *
 * Port of desktop-client's ApiClient: always talks to the gateway, attaches a
 * Bearer token when one is set, and normalises the backend's JSON error shape
 * into a single ApiError type.
 */

import { clearSession, getToken, refreshSession } from './session';
import type {
  AddDiaryRequest,
  CreateIngredientRequest,
  CreateRecipeRequest,
  DailyNutritionPoint,
  DailyWaterPoint,
  DaySummary,
  DiaryEntry,
  Ingredient,
  IsoDate,
  Meal,
  Objective,
  ParseResult,
  ParsedRecipe,
  Profile,
  Recipe,
  UpdateObjectiveRequest,
  UpdateProfileRequest,
  WeightEntry,
} from './types';

/** Requests that hang forever would leave the UI stuck in a loading state. */
const TIMEOUT_MS = 20_000;
/** The AI providers can take a while to answer; give those calls more room. */
const AI_TIMEOUT_MS = 60_000;

/**
 * The user's IANA zone. The server uses it to decide what "today" means when
 * a request doesn't name a date (e.g. snapshotting a new objective), so a user
 * east of UTC editing after midnight gets today, not yesterday.
 */
const TIME_ZONE: string | undefined = (() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return undefined;
  }
})();

export class ApiError extends Error {
  readonly status: number;
  readonly body: string;

  constructor(status: number, message: string, body: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }

  /** The backend refused the credentials — the caller should re-authenticate. */
  get isAuthFailure(): boolean {
    return this.status === 401 || this.status === 403;
  }
}

/** Backend error envelope produced by GlobalExceptionHandler. */
interface ErrorEnvelope {
  message?: string;
  error?: string;
  status?: number;
}

/** A plain sentence for a status, when the server didn't send its own message. Never shows the code. */
export function statusMessage(status: number): string {
  if (status === 400) return "Some of the details aren't valid. Please check them and try again.";
  if (status === 401) return 'Your session expired. Please sign in again.';
  if (status === 403) return "You don't have permission to do that.";
  if (status === 404) return "We couldn't find that. It may have been deleted.";
  if (status === 409) return "That can't be done right now.";
  if (status === 413) return "That's too much to send at once.";
  if (status === 426) return 'Your browser is too old for a secure connection. Please update it.';
  if (status === 429) return 'Too many requests. Please wait a moment and try again.';
  return 'Something went wrong on our side. Please try again in a moment.';
}

function messageFrom(status: number, raw: string): string {
  try {
    const parsed = JSON.parse(raw) as ErrorEnvelope;
    // Our services always send a readable "message"; anything else (a proxy's
    // HTML error page, "Bad Request"...) is replaced by a plain sentence.
    if (parsed.message) return parsed.message;
  } catch {
    /* not JSON — fall through */
  }
  return statusMessage(status);
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  timeoutMs?: number;
  signal?: AbortSignal;
}

async function request<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const { method = 'GET', body, timeoutMs = TIMEOUT_MS, signal } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (TIME_ZONE) headers['X-Time-Zone'] = TIME_ZONE;

  const timeoutController = new AbortController();
  const timer = window.setTimeout(() => timeoutController.abort(), timeoutMs);
  // Abort if either the caller cancels (component unmount) or we time out.
  const composed = signal
    ? AbortSignal.any([signal, timeoutController.signal])
    : timeoutController.signal;

  let response: Response;
  try {
    // Always same-origin: nginx (local) or CloudFront (AWS) routes /api to the
    // gateway, so no CORS is involved and the CSP stays at connect-src 'self'.
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: composed,
      // No cookies are used; keeping this explicit stops a future same-site
      // cookie from being sent by accident.
      credentials: 'omit',
      redirect: 'error',
    });
  } catch (err) {
    if (signal?.aborted) throw err;
    if (timeoutController.signal.aborted) {
      throw new ApiError(0, 'The server took too long to respond. Try again.', '');
    }
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?', String(err));
  } finally {
    window.clearTimeout(timer);
  }

  if (!response.ok) {
    const raw = await response.text().catch(() => '');
    if (response.status === 401) {
      // The access token was rejected (expired or revoked). Try the refresh
      // token once; if that fails too, drop the session so the app falls back
      // to the sign-in screen instead of looping on 401s.
      if (!retried && token && (await refreshSession())) {
        return request<T>(path, options, true);
      }
      clearSession();
    }
    throw new ApiError(response.status, messageFrom(response.status, raw), raw);
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

function query(params: Record<string, string | number | undefined>): string {
  const usable = Object.entries(params).filter(([, v]) => v !== undefined && v !== '');
  if (usable.length === 0) return '';
  const search = new URLSearchParams();
  for (const [k, v] of usable) search.set(k, String(v));
  return `?${search.toString()}`;
}

export const api = {
  // ---------------- profile ----------------
  getProfile: (signal?: AbortSignal) => request<Profile>('/api/profile', { signal }),
  updateProfile: (body: UpdateProfileRequest) =>
    request<Profile>('/api/profile', { method: 'PUT', body }),
  /** Deletes the account's data on the server. Irreversible. */
  deleteProfile: () => request<void>('/api/profile', { method: 'DELETE' }),

  // ---------------- objective ----------------
  getObjective: (signal?: AbortSignal) => request<Objective>('/api/objective', { signal }),
  updateObjective: (body: UpdateObjectiveRequest) =>
    request<Objective>('/api/objective', { method: 'PUT', body }),

  // ---------------- weight ----------------
  listWeight: (signal?: AbortSignal) => request<WeightEntry[]>('/api/weight', { signal }),
  upsertWeight: (date: IsoDate, weightKg: number) =>
    request<WeightEntry>('/api/weight', { method: 'POST', body: { date, weightKg } }),
  deleteWeight: (id: number) => request<void>(`/api/weight/${id}`, { method: 'DELETE' }),

  // ---------------- reports ----------------
  nutritionReport: (from: IsoDate, to: IsoDate, signal?: AbortSignal) =>
    request<DailyNutritionPoint[]>(`/api/reports/nutrition${query({ from, to })}`, { signal }),
  waterReport: (from: IsoDate, to: IsoDate, signal?: AbortSignal) =>
    request<DailyWaterPoint[]>(`/api/reports/water${query({ from, to })}`, { signal }),

  // ---------------- ingredients ----------------
  /** Only the caller's own foods (the "My library" tables). */
  listMyIngredients: (q: string, page: number, size: number, signal?: AbortSignal) =>
    request<Ingredient[]>(`/api/ingredients${query({ q, page, size, scope: 'mine' })}`, { signal }),
  /** Own + public library (pickers: add-to-diary, recipe builder, compare). */
  searchAllIngredients: (q: string, page: number, size: number, signal?: AbortSignal) =>
    request<Ingredient[]>(`/api/ingredients${query({ q, page, size, scope: 'all' })}`, { signal }),
  getIngredient: (id: number, signal?: AbortSignal) =>
    request<Ingredient>(`/api/ingredients/${id}`, { signal }),
  createIngredient: (body: CreateIngredientRequest) =>
    request<Ingredient>('/api/ingredients', { method: 'POST', body }),
  updateIngredient: (id: number, body: CreateIngredientRequest) =>
    request<Ingredient>(`/api/ingredients/${id}`, { method: 'PUT', body }),
  deleteIngredient: (id: number) => request<void>(`/api/ingredients/${id}`, { method: 'DELETE' }),

  // ---------------- recipes ----------------
  listMyRecipes: (q: string, page: number, size: number, signal?: AbortSignal) =>
    request<Recipe[]>(`/api/recipes${query({ q, page, size, scope: 'mine' })}`, { signal }),
  searchAllRecipes: (q: string, page: number, size: number, signal?: AbortSignal) =>
    request<Recipe[]>(`/api/recipes${query({ q, page, size, scope: 'all' })}`, { signal }),
  createRecipe: (body: CreateRecipeRequest) =>
    request<Recipe>('/api/recipes', { method: 'POST', body }),
  updateRecipe: (id: number, body: CreateRecipeRequest) =>
    request<Recipe>(`/api/recipes/${id}`, { method: 'PUT', body }),
  deleteRecipe: (id: number) => request<void>(`/api/recipes/${id}`, { method: 'DELETE' }),

  // ---------------- diary ----------------
  addDiary: (body: AddDiaryRequest) => request<DiaryEntry>('/api/diary', { method: 'POST', body }),
  deleteDiary: (id: number) => request<void>(`/api/diary/${id}`, { method: 'DELETE' }),
  moveDiary: (id: number, date: IsoDate, meal: Meal) =>
    request<DiaryEntry>(`/api/diary/${id}/move`, { method: 'POST', body: { date, meal } }),
  copyDiary: (id: number, date: IsoDate, meal: Meal) =>
    request<DiaryEntry>(`/api/diary/${id}/copy`, { method: 'POST', body: { date, meal } }),

  // ---------------- water ----------------
  addWater: (date: IsoDate, ml: number) =>
    request<void>('/api/water', { method: 'POST', body: { date, ml } }),
  deleteWater: (id: number) => request<void>(`/api/water/${id}`, { method: 'DELETE' }),

  // ---------------- summary ----------------
  getDaySummary: (date: IsoDate, signal?: AbortSignal) =>
    request<DaySummary>(`/api/summary${query({ date })}`, { signal }),

  // ---------------- AI ----------------
  parseIngredients: (text: string) =>
    request<ParseResult>('/api/ai/parse', {
      method: 'POST',
      body: { text },
      timeoutMs: AI_TIMEOUT_MS,
    }),
  parseRecipe: (text: string) =>
    request<ParsedRecipe>('/api/ai/parse-recipe', {
      method: 'POST',
      body: { text },
      timeoutMs: AI_TIMEOUT_MS,
    }),
  /**
   * Persisting an AI blueprint is a DB write, so it lives in backend-core, not
   * the stateless ai-service. The gateway routes /api/recipes/** there.
   */
  saveAiRecipe: (blueprint: ParsedRecipe) =>
    request<Recipe>('/api/recipes/from-ai', { method: 'POST', body: blueprint }),
};
