/**
 * Modal dialog shell used by every `*Dialog` view. Mirrors the JavaFX
 * Dialog<T> pattern: header text, scrollable body, OK/Cancel button bar, and
 * validation that keeps the dialog open.
 */

import { useEffect, useRef, type ReactNode } from 'react';

interface ModalProps {
  title: string;
  headerText?: string;
  /** `wide` ≈ 920px (recipe builder), `xwide` ≈ 1040px (rule editor). */
  size?: 'normal' | 'wide' | 'xwide';
  children: ReactNode;
  /** Footer buttons. Omit to render the default OK / Cancel pair. */
  footer?: ReactNode;
  okLabel?: string;
  okDisabled?: boolean;
  busy?: boolean;
  onOk?: () => void;
  onCancel: () => void;
}

export function Modal({
  title,
  headerText,
  size = 'normal',
  children,
  footer,
  okLabel = 'OK',
  okDisabled = false,
  busy = false,
  onOk,
  onCancel,
}: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  // Escape closes; focus moves into the dialog so keyboard users aren't
  // stranded behind it.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onCancel();
      }
    };
    document.addEventListener('keydown', onKey);
    const firstField = panelRef.current?.querySelector<HTMLElement>(
      'input, select, textarea, button',
    );
    firstField?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        // Click-outside cancels, but only when the press started on the backdrop
        // — dragging a text selection out of the dialog shouldn't close it.
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={panelRef}
        className={`modal ${size === 'normal' ? '' : size}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          <h2 className="section-title">{title}</h2>
          {headerText ? <p className="muted">{headerText}</p> : null}
        </div>
        <div className="modal-body">{children}</div>
        <div className="modal-footer">
          {footer ?? (
            <>
              <button type="button" className="btn" onClick={onCancel} disabled={busy}>
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={onOk}
                disabled={okDisabled || busy}
              >
                {busy ? 'Saving…' : okLabel}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
