/**
 * Runtime configuration.
 *
 * Mirrors desktop-client's AppConfig: build-time Vite env vars, with sane
 * local-dev defaults. Nothing account-specific is committed — copy
 * `.env.example` to `.env.local` and fill it in for cloud mode.
 */

/** `none` = backend running under the `dev` profile (no auth). `cognito` = real sign-in. */
export const AUTH_MODE: 'none' | 'cognito' =
  import.meta.env.VITE_AUTH_MODE === 'cognito' ? 'cognito' : 'none';

/** e.g. eu-central-1_AbCdEf123. The region is its prefix. */
export const COGNITO_USER_POOL_ID: string = import.meta.env.VITE_COGNITO_USER_POOL_ID ?? '';
export const COGNITO_REGION: string = COGNITO_USER_POOL_ID.split('_')[0] ?? '';
export const COGNITO_CLIENT_ID: string = import.meta.env.VITE_COGNITO_CLIENT_ID ?? '';

export const APP_NAME = 'Calorie Tracker';
export const APP_TAGLINE = 'Log meals, track macros, hit your goals.';
