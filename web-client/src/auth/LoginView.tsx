/**
 * Sign-in / Register screen — the web port of the JavaFX login dialog:
 * a two-tab card (Sign in · Register), the emailed 6-digit confirm step, and
 * the forgot-password flow (request code → set new password).
 */

import { useState, type FormEvent } from 'react';
import { CognitoError } from '../api/cognito';
import { APP_NAME, APP_TAGLINE } from '../api/config';
import { friendlyMessage } from '../components/Toast';
import { useAuth } from './AuthContext';
import { PasswordField, PasswordRules } from './PasswordField';
import { meetsPasswordRules } from './password';

type Tab = 'signin' | 'register';
type Status = { text: string; ok: boolean } | null;
/** Forgot-password flow: ask for the email, then the code + new password. */
type Reset = { stage: 'request' | 'confirm' } | null;

const RULES_NOT_MET = "The password doesn't meet all the requirements listed under it yet.";

export function LoginView() {
  const auth = useAuth();
  const [tab, setTab] = useState<Tab>('signin');
  const [status, setStatus] = useState<Status>(null);
  const [busy, setBusy] = useState(false);

  // Sign in
  const [siEmail, setSiEmail] = useState('');
  const [siPassword, setSiPassword] = useState('');

  // Register
  const [suEmail, setSuEmail] = useState('');
  const [suPassword, setSuPassword] = useState('');
  const [suPassword2, setSuPassword2] = useState('');

  // Confirm step — non-null once SignUp succeeded and we're waiting on the code.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);
  const [code, setCode] = useState('');

  // Forgot password
  const [reset, setReset] = useState<Reset>(null);
  const [resetEmail, setResetEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPassword2, setNewPassword2] = useState('');

  function switchTab(next: Tab) {
    setTab(next);
    setStatus(null);
  }

  async function onSignIn(e: FormEvent) {
    e.preventDefault();
    setStatus(null);
    if (!siEmail.trim() || !siPassword) {
      setStatus({ text: 'Email and password are required.', ok: false });
      return;
    }
    setBusy(true);
    try {
      await auth.signIn(siEmail, siPassword);
      // On success the app re-renders into the shell; nothing else to do.
    } catch (err) {
      if (err instanceof CognitoError && err.type === 'UserNotConfirmedException') {
        // Signed up but never entered the code: take them straight to that step.
        const email = siEmail.trim();
        setPendingEmail(email);
        setCode('');
        try {
          await auth.resendCode(email);
          setStatus({ text: "Your email isn't confirmed yet. We've sent you a new code.", ok: true });
        } catch (resendErr) {
          setStatus({ text: friendlyMessage(resendErr), ok: false });
        }
      } else {
        setStatus({ text: friendlyMessage(err), ok: false });
      }
    } finally {
      setBusy(false);
    }
  }

  async function onRegister(e: FormEvent) {
    e.preventDefault();
    setStatus(null);
    if (!suEmail.trim() || !suPassword) {
      setStatus({ text: 'Email and password are required.', ok: false });
      return;
    }
    if (!meetsPasswordRules(suPassword)) {
      setStatus({ text: RULES_NOT_MET, ok: false });
      return;
    }
    if (suPassword !== suPassword2) {
      setStatus({ text: "The two passwords don't match.", ok: false });
      return;
    }
    setBusy(true);
    try {
      await auth.register(suEmail, suPassword);
      setPendingEmail(suEmail.trim());
      setCode('');
    } catch (err) {
      setStatus({ text: friendlyMessage(err), ok: false });
    } finally {
      setBusy(false);
    }
  }

  async function onConfirm(e: FormEvent) {
    e.preventDefault();
    setStatus(null);
    if (!code.trim()) {
      setStatus({ text: 'Enter the code from the email.', ok: false });
      return;
    }
    setBusy(true);
    try {
      await auth.confirm(pendingEmail!, code);
      // Mirror the desktop flow: hop back to Sign in with the email prefilled.
      setSiEmail(pendingEmail!);
      setSiPassword('');
      setPendingEmail(null);
      setSuPassword('');
      setSuPassword2('');
      setTab('signin');
      setStatus({ text: 'Account confirmed — sign in with your password.', ok: true });
    } catch (err) {
      setStatus({ text: friendlyMessage(err), ok: false });
    } finally {
      setBusy(false);
    }
  }

  async function onResend() {
    setStatus(null);
    try {
      await auth.resendCode(pendingEmail!);
      setStatus({ text: 'A new code is on its way.', ok: true });
    } catch (err) {
      setStatus({ text: friendlyMessage(err), ok: false });
    }
  }

  function startReset() {
    setResetEmail(siEmail.trim());
    setResetCode('');
    setNewPassword('');
    setNewPassword2('');
    setStatus(null);
    setReset({ stage: 'request' });
  }

  async function onRequestReset(e: FormEvent) {
    e.preventDefault();
    setStatus(null);
    if (!resetEmail.trim()) {
      setStatus({ text: 'Enter the email you registered with.', ok: false });
      return;
    }
    setBusy(true);
    try {
      await auth.forgotPassword(resetEmail);
      setReset({ stage: 'confirm' });
      setStatus({ text: 'If that address has an account, a reset code is on its way.', ok: true });
    } catch (err) {
      setStatus({ text: friendlyMessage(err), ok: false });
    } finally {
      setBusy(false);
    }
  }

  async function onConfirmReset(e: FormEvent) {
    e.preventDefault();
    setStatus(null);
    if (!resetCode.trim() || !newPassword) {
      setStatus({ text: 'Enter the code and a new password.', ok: false });
      return;
    }
    if (!meetsPasswordRules(newPassword)) {
      setStatus({ text: RULES_NOT_MET, ok: false });
      return;
    }
    if (newPassword !== newPassword2) {
      setStatus({ text: "The two passwords don't match.", ok: false });
      return;
    }
    setBusy(true);
    try {
      await auth.resetPassword(resetEmail, resetCode, newPassword);
      setSiEmail(resetEmail.trim());
      setSiPassword('');
      setReset(null);
      setTab('signin');
      setStatus({ text: 'Password changed — sign in with the new one.', ok: true });
    } catch (err) {
      setStatus({ text: friendlyMessage(err), ok: false });
    } finally {
      setBusy(false);
    }
  }

  const statusBox = status ? (
    <div className={`auth-status ${status.ok ? 'auth-status-ok' : 'auth-status-error'}`}>
      {status.text}
    </div>
  ) : null;

  // ---- forgot-password flow replaces the tabs entirely ----
  if (reset) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-brand">Reset your password</div>
          <div className="auth-tagline">
            {reset.stage === 'request'
              ? "We'll email you a code to set a new password."
              : `Enter the code sent to ${resetEmail} and choose a new password.`}
          </div>
          {reset.stage === 'request' ? (
            <form className="auth-form" style={{ marginTop: 18 }} onSubmit={onRequestReset}>
              <div>
                <label className="auth-field-label" htmlFor="reset-email">
                  Email
                </label>
                <input
                  id="reset-email"
                  type="email"
                  className="auth-field"
                  autoComplete="username"
                  placeholder="you@example.com"
                  value={resetEmail}
                  onChange={(e) => setResetEmail(e.target.value)}
                  autoFocus
                />
              </div>
              {statusBox}
              <div className="row">
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  {busy ? 'Sending…' : 'Send code'}
                </button>
                <span className="spacer" />
                <button type="button" className="btn" onClick={() => setReset(null)} disabled={busy}>
                  Back
                </button>
              </div>
            </form>
          ) : (
            <form className="auth-form" style={{ marginTop: 18 }} onSubmit={onConfirmReset}>
              <div>
                <label className="auth-field-label" htmlFor="reset-code">
                  Reset code
                </label>
                <input
                  id="reset-code"
                  className="auth-field"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="123456"
                  value={resetCode}
                  onChange={(e) => setResetCode(e.target.value)}
                  autoFocus
                />
              </div>
              <div>
                <label className="auth-field-label" htmlFor="reset-password">
                  New password
                </label>
                <PasswordField
                  id="reset-password"
                  autoComplete="new-password"
                  placeholder="choose a new password"
                  value={newPassword}
                  onChange={setNewPassword}
                />
              </div>
              <div>
                <label className="auth-field-label" htmlFor="reset-password2">
                  Confirm new password
                </label>
                <PasswordField
                  id="reset-password2"
                  autoComplete="new-password"
                  placeholder="repeat the password"
                  value={newPassword2}
                  onChange={setNewPassword2}
                />
                <PasswordRules password={newPassword} confirm={newPassword2} />
              </div>
              {statusBox}
              <div className="row">
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  {busy ? 'Saving…' : 'Set new password'}
                </button>
                <span className="spacer" />
                <button
                  type="button"
                  className="btn"
                  onClick={() => setReset({ stage: 'request' })}
                  disabled={busy}
                >
                  Back
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    );
  }

  // ---- confirm-code step replaces the tabs entirely ----
  if (pendingEmail) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-brand">Check your inbox</div>
          <div className="auth-tagline">We sent a 6-digit code to {pendingEmail}.</div>
          <form className="auth-form" style={{ marginTop: 18 }} onSubmit={onConfirm}>
            <div>
              <label className="auth-field-label" htmlFor="code">
                Verification code
              </label>
              <input
                id="code"
                className="auth-field"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                autoFocus
              />
            </div>
            {statusBox}
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? 'Verifying…' : 'Confirm'}
              </button>
              <button type="button" className="btn" onClick={onResend} disabled={busy}>
                Resend code
              </button>
              <span className="spacer" />
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setPendingEmail(null);
                  setStatus(null);
                }}
                disabled={busy}
              >
                Back
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">{APP_NAME}</div>
        <div className="auth-tagline">{APP_TAGLINE}</div>

        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'signin'}
            className={`auth-tab ${tab === 'signin' ? 'active' : ''}`}
            onClick={() => switchTab('signin')}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'register'}
            className={`auth-tab ${tab === 'register' ? 'active' : ''}`}
            onClick={() => switchTab('register')}
          >
            Register
          </button>
        </div>

        {tab === 'signin' ? (
          <form className="auth-form" onSubmit={onSignIn}>
            <div>
              <label className="auth-field-label" htmlFor="si-email">
                Email
              </label>
              <input
                id="si-email"
                type="email"
                className="auth-field"
                autoComplete="username"
                placeholder="you@example.com"
                value={siEmail}
                onChange={(e) => setSiEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="auth-field-label" htmlFor="si-password">
                Password
              </label>
              <PasswordField
                id="si-password"
                autoComplete="current-password"
                placeholder="password"
                value={siPassword}
                onChange={setSiPassword}
              />
            </div>
            {statusBox}
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? 'Signing in…' : 'Continue'}
              </button>
              <span className="spacer" />
              <button type="button" className="btn-link" onClick={startReset} disabled={busy}>
                Forgot password?
              </button>
            </div>
          </form>
        ) : (
          <form className="auth-form" onSubmit={onRegister}>
            <div>
              <label className="auth-field-label" htmlFor="su-email">
                Email
              </label>
              <input
                id="su-email"
                type="email"
                className="auth-field"
                autoComplete="username"
                placeholder="you@example.com"
                value={suEmail}
                onChange={(e) => setSuEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="auth-field-label" htmlFor="su-password">
                Password
              </label>
              <PasswordField
                id="su-password"
                autoComplete="new-password"
                placeholder="choose a password"
                value={suPassword}
                onChange={setSuPassword}
              />
            </div>
            <div>
              <label className="auth-field-label" htmlFor="su-password2">
                Confirm password
              </label>
              <PasswordField
                id="su-password2"
                autoComplete="new-password"
                placeholder="repeat the password"
                value={suPassword2}
                onChange={setSuPassword2}
              />
              <PasswordRules password={suPassword} confirm={suPassword2} />
            </div>
            <p className="auth-hint">
              We'll email a 6-digit code to confirm your address. Enter it on the next screen, then
              come back to sign in.
            </p>
            {statusBox}
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Creating account…' : 'Continue'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
