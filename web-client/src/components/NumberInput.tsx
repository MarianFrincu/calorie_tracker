/**
 * A number field that behaves the way people expect:
 *   - clearing it leaves it empty (not a 0 that then turns "67" into "067");
 *   - leading zeros are dropped as you type, "0.5" stays;
 *   - a comma works as the decimal point ("75,5");
 *   - out-of-range values are reported by the browser's normal form
 *     validation on submit, in plain words.
 *
 * It's a text input with a numeric keyboard rather than type="number": the
 * browser's number input silently reports half-typed values like "75." as
 * empty, which made controlled fields wipe what the user was typing.
 */

import { useEffect, useRef, useState, type InputHTMLAttributes } from 'react';

interface Props
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type' | 'min' | 'max'> {
  value: number | null;
  onChange: (value: number | null) => void;
  /** Whole numbers only (age, ml...). */
  integer?: boolean;
  /** Allow a leading minus sign. */
  allowNegative?: boolean;
  min?: number;
  max?: number;
  /** What an empty field means to the caller (default: null = "not filled in"). */
  emptyValue?: number | null;
}

const toText = (n: number | null) => (n == null ? '' : String(n));

export function NumberInput({
  value,
  onChange,
  integer,
  allowNegative,
  min,
  max,
  emptyValue = null,
  ...rest
}: Props) {
  const [text, setText] = useState(() => toText(value));
  const ref = useRef<HTMLInputElement>(null);

  const incomplete = (t: string) => t === '' || t === '.' || t === '-' || t === '-.';
  const parsed = incomplete(text) ? emptyValue : Number(text);

  // Follow changes made from outside (a prefill, a reset) - but not the echo
  // of what the user just typed, or an empty field would snap back to "0".
  useEffect(() => {
    if (value !== parsed) setText(toText(value));
  }, [value]); // only when the value changes, never on typing

  // Range check through the browser's own form validation.
  useEffect(() => {
    const n = incomplete(text) ? null : Number(text);
    let message = '';
    if (n != null && Number.isFinite(n)) {
      if (min != null && max != null && (n < min || n > max)) message = `Enter a number from ${min} to ${max}.`;
      else if (min != null && n < min) message = `Enter ${min} or more.`;
      else if (max != null && n > max) message = `Enter ${max} or less.`;
    }
    ref.current?.setCustomValidity(message);
  }, [text, min, max]);

  return (
    <input
      {...rest}
      ref={ref}
      type="text"
      inputMode={integer ? 'numeric' : 'decimal'}
      autoComplete="off"
      value={text}
      onChange={(e) => {
        let next = e.target.value.replace(',', '.');
        const sign = allowNegative ? '-?' : '';
        const allowed = new RegExp(integer ? `^${sign}\\d*$` : `^${sign}\\d*\\.?\\d*$`);
        if (!allowed.test(next)) return; // ignore letters, a second dot, stray signs
        next = next.replace(/^(-?)0+(?=\d)/, '$1');
        setText(next);
        onChange(incomplete(next) ? emptyValue : Number(next));
      }}
    />
  );
}
