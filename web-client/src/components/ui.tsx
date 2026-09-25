/** Small presentational primitives shared by every view. */

import type { ReactNode } from 'react';
import { fmt } from '../lib/format';

export type MacroVariant =
  | 'protein'
  | 'carbs'
  | 'fat'
  | 'fiber'
  | 'library-check'
  | 'library-app'
  | 'library-mine'
  | 'library-missing';

export function MacroTag({ text, variant }: { text: string; variant: MacroVariant }) {
  return <span className={`macro-tag ${variant}`}>{text}</span>;
}

/** The P / C / F / Fib chip row used on diary rows and AI results. */
export function MacroTags({
  protein,
  carbs,
  fat,
  fiber,
  suffix = 'g',
}: {
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  suffix?: string;
}) {
  return (
    <div className="chips">
      <MacroTag text={`P ${fmt(protein)}${suffix}`} variant="protein" />
      <MacroTag text={`C ${fmt(carbs)}${suffix}`} variant="carbs" />
      <MacroTag text={`F ${fmt(fat)}${suffix}`} variant="fat" />
      <MacroTag text={`Fib ${fmt(fiber)}${suffix}`} variant="fiber" />
    </div>
  );
}

export function ProgressBar({
  value,
  variant,
  height = 14,
}: {
  /** 0..1; clamped. */
  value: number;
  variant?: 'water' | 'protein' | 'carbs' | 'fat' | 'fiber';
  height?: number;
}) {
  const pct = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0)) * 100;
  return (
    <div
      className={`progress ${variant ?? ''}`}
      style={{ height }}
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div className="bar" style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Card({
  title,
  children,
  className = '',
}: {
  title?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {title ? <div className="card-title">{title}</div> : null}
      {children}
    </section>
  );
}

/** Collapsible meal panel — the web twin of JavaFX's TitledPane. */
export function TitledPane({
  title,
  expanded,
  onToggle,
  children,
}: {
  title: ReactNode;
  expanded: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <div className={`titled-pane ${expanded ? '' : 'collapsed'}`}>
      <button type="button" className="tp-title" onClick={onToggle} aria-expanded={expanded}>
        <span className="tp-chevron">▼</span>
        <span className="grow">{title}</span>
      </button>
      {expanded ? <div className="tp-content">{children}</div> : null}
    </div>
  );
}

export function Tabs<T extends string>({
  tabs,
  active,
  onChange,
}: {
  tabs: readonly { id: T; label: string }[];
  active: T;
  onChange: (id: T) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={t.id === active}
          className={`tab ${t.id === active ? 'active' : ''}`}
          onClick={() => onChange(t.id)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <label className="col" style={{ gap: 6 }}>
      <span className="auth-field-label" style={{ marginBottom: 0 }}>
        {label}
      </span>
      {children}
      {hint ? <span className="auth-hint">{hint}</span> : null}
    </label>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="table-placeholder">{children}</div>;
}

export function Loading({ what = 'data' }: { what?: string }) {
  return <div className="skeleton">Loading {what}…</div>;
}
