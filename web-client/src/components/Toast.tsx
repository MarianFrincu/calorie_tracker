/**
 * Toast notifications — the web equivalent of the desktop client's
 * Async.showError / Async.showWarning alerts.
 */

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { ApiError } from '../api/client';

type ToastKind = 'info' | 'warn' | 'error';

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  info: (message: string) => void;
  warn: (message: string) => void;
  error: (message: string) => void;
  /** Turns any thrown value into a friendly message. Mirrors Messages.friendly. */
  showError: (err: unknown) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** Human-readable text for anything the API or the network can throw at us. */
export function friendlyMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 401) return 'Your session expired. Please sign in again.';
    if (err.status === 403) return "You don't have permission to do that.";
    if (err.status === 404) return 'That item no longer exists — the list may be out of date.';
    if (err.status === 409) return err.message;
    if (err.status === 0) return err.message;
    return err.message;
  }
  if (err instanceof Error) return err.message;
  return String(err);
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, kind, message }]);
      window.setTimeout(() => dismiss(id), kind === 'error' ? 8000 : 4500);
    },
    [dismiss],
  );

  const value = useMemo<ToastApi>(
    () => ({
      info: (m) => push('info', m),
      warn: (m) => push('warn', m),
      error: (m) => push('error', m),
      showError: (err) => push('error', friendlyMessage(err)),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`}>
            <span className="grow">{t.message}</span>
            <button
              type="button"
              className="toast-close"
              onClick={() => dismiss(t.id)}
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
