/**
 * A password input with a Show/Hide toggle, and the live rules checklist
 * shown under new-password fields.
 */

import { useState } from 'react';

import { PASSWORD_RULES } from './password';

interface FieldProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: 'current-password' | 'new-password';
  placeholder?: string;
  autoFocus?: boolean;
}

export function PasswordField({ id, value, onChange, autoComplete, placeholder, autoFocus }: FieldProps) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="password-field">
      <input
        id={id}
        type={visible ? 'text' : 'password'}
        className="auth-field"
        autoComplete={autoComplete}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
        spellCheck={false}
        autoCapitalize="off"
      />
      <button
        type="button"
        className="password-toggle"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        aria-pressed={visible}
      >
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  );
}

interface RulesProps {
  password: string;
  /** The confirm field's value; adds a "Passwords match" line once typed. */
  confirm?: string;
}

export function PasswordRules({ password, confirm }: RulesProps) {
  const rows = PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.met(password) }));
  if (confirm !== undefined && confirm !== '') {
    rows.push({ label: 'Both passwords match', met: password === confirm });
  }
  return (
    <ul className="password-rules" aria-label="Password requirements">
      {rows.map((row) => (
        <li key={row.label} className={row.met ? 'met' : ''}>
          <span className="password-rule-mark" aria-hidden="true">
            {row.met ? '✓' : '○'}
          </span>
          {row.label}
          <span className="sr-only">{row.met ? ' (done)' : ' (missing)'}</span>
        </li>
      ))}
    </ul>
  );
}
