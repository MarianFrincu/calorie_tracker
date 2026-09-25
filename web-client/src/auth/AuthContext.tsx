/**
 * Auth state for the whole app.
 *
 * Two modes, matching the desktop client:
 *   - `none`    — backend runs under the `dev` profile, every call maps to
 *                 `dev-user`. No sign-in screen.
 *   - `cognito` — real sign-in; a Cognito access token is attached as Bearer.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

import { api } from '../api/client';
import { AUTH_MODE, COGNITO_CLIENT_ID, COGNITO_REGION, COGNITO_USER_POOL_ID } from '../api/config';
import * as cognito from '../api/cognito';
import { clearSession, getToken, onSessionChange, setSession } from '../api/session';

interface AuthState {
  mode: 'none' | 'cognito';
  /** True when the app may talk to the API. Always true in `none` mode. */
  signedIn: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  confirm: (email: string, code: string) => Promise<void>;
  resendCode: (email: string) => Promise<void>;
  forgotPassword: (email: string) => Promise<void>;
  resetPassword: (email: string, code: string, newPassword: string) => Promise<void>;
  /** Deletes all server-side data and (in cognito mode) the identity itself. */
  deleteAccount: () => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function requireCognitoConfig(): void {
  if (!COGNITO_USER_POOL_ID || !COGNITO_CLIENT_ID) {
    throw new Error(
      'Cognito is not configured. Set VITE_COGNITO_USER_POOL_ID and VITE_COGNITO_CLIENT_ID ' +
        '(see web-client/.env.example) and rebuild.',
    );
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [hasToken, setHasToken] = useState(() => getToken() != null);

  const navigate = useNavigate();

  // Leaving a session (sign-out, expiry, deletion) clears the cache and goes
  // back to Day right away. Clearing after the next sign-in instead would
  // cancel the queries its screens had just started, stuck on "Loading…".
  useEffect(
    () =>
      onSessionChange((token) => {
        if (token == null) {
          queryClient.clear();
          navigate('/', { replace: true });
        }
        setHasToken(token != null);
      }),
    [queryClient, navigate],
  );

  const signIn = useCallback(async (email: string, password: string) => {
    requireCognitoConfig();
    setSession(await cognito.login(COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID, email.trim(), password));
  }, []);

  const register = useCallback(async (email: string, password: string) => {
    requireCognitoConfig();
    await cognito.signUp(COGNITO_REGION, COGNITO_CLIENT_ID, email.trim(), password);
  }, []);

  const confirm = useCallback(async (email: string, code: string) => {
    requireCognitoConfig();
    await cognito.confirmSignUp(COGNITO_REGION, COGNITO_CLIENT_ID, email.trim(), code.trim());
  }, []);

  const resendCode = useCallback(async (email: string) => {
    requireCognitoConfig();
    await cognito.resendCode(COGNITO_REGION, COGNITO_CLIENT_ID, email.trim());
  }, []);

  const forgotPassword = useCallback(async (email: string) => {
    requireCognitoConfig();
    await cognito.forgotPassword(COGNITO_REGION, COGNITO_CLIENT_ID, email.trim());
  }, []);

  const resetPassword = useCallback(async (email: string, code: string, newPassword: string) => {
    requireCognitoConfig();
    await cognito.confirmForgotPassword(
      COGNITO_REGION,
      COGNITO_CLIENT_ID,
      email.trim(),
      code.trim(),
      newPassword,
    );
  }, []);

  const deleteAccount = useCallback(async () => {
    // Data first: if the identity went first and the data delete then failed,
    // the user could never sign in again to retry.
    await api.deleteProfile();
    if (AUTH_MODE === 'cognito') {
      const token = getToken();
      if (token) await cognito.deleteUser(COGNITO_REGION, token);
      clearSession();
    } else {
      // Dev mode has no session to end. Reset (not clear) the cache: reset
      // refetches what's on screen, so the now-empty profile shows up and the
      // onboarding lock kicks in. clear() would leave mounted views stuck.
      await queryClient.resetQueries();
    }
  }, [queryClient]);

  const signOut = useCallback(() => {
    clearSession({ revoke: true });
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      mode: AUTH_MODE,
      signedIn: AUTH_MODE === 'none' || hasToken,
      signIn,
      register,
      confirm,
      resendCode,
      forgotPassword,
      resetPassword,
      deleteAccount,
      signOut,
    }),
    [hasToken, signIn, register, confirm, resendCode, forgotPassword, resetPassword, deleteAccount, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
