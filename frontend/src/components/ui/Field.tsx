import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { clsx } from 'clsx';

interface FieldShell {
  label: string;
  hint?: ReactNode;
  error?: string | null;
  className?: string;
}

function Shell({ id, label, hint, error, className, children }: FieldShell & { id: string; children: ReactNode }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="label">{label}</label>
      {children}
      {error ? (
        <p className="mt-1.5 text-xs text-rose-300" role="alert">{error}</p>
      ) : (
        hint && <p className="mt-1.5 text-xs text-ink-500">{hint}</p>
      )}
    </div>
  );
}

export function Input({ label, hint, error, className, ...rest }: FieldShell & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className}>
      <input id={id} className={clsx('field', error && 'border-rose-400/50')} aria-invalid={!!error} {...rest} />
    </Shell>
  );
}

export function Select({ label, hint, error, className, children, ...rest }: FieldShell & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className}>
      <select id={id} className={clsx('field appearance-none bg-[length:16px] bg-[right_12px_center] bg-no-repeat pr-9')} style={{
        backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238a93a6' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
      }} {...rest}>
        {children}
      </select>
    </Shell>
  );
}

export function TextArea({ label, hint, error, className, ...rest }: FieldShell & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className}>
      <textarea id={id} className="field min-h-20 resize-y" {...rest} />
    </Shell>
  );
}

/** Pill-style single choice; nicer than a select for 2-5 options. */
export function Segmented<T extends string>({ label, value, options, onChange, className }: {
  label?: string;
  value: T;
  options: { value: T; label: ReactNode; color?: string }[];
  onChange: (v: T) => void;
  className?: string;
}) {
  return (
    <div className={className}>
      {label && <span className="label">{label}</span>}
      <div className="flex flex-wrap gap-1.5 rounded-2xl border border-white/6 bg-night-800/60 p-1">
        {options.map((o) => {
          const active = o.value === value;
          return (
            <button
              key={o.value}
              type="button"
              onClick={() => onChange(o.value)}
              aria-pressed={active}
              className={clsx(
                'flex-1 rounded-xl px-3 py-2 text-xs font-semibold transition',
                active ? 'bg-white/10 text-ink-100 shadow-inner' : 'text-ink-400 hover:text-ink-200',
              )}
              style={active && o.color ? { color: o.color, background: `color-mix(in oklab, ${o.color} 14%, transparent)` } : undefined}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
