import type { ReactNode } from 'react';
import { clsx } from 'clsx';

interface BadgeProps {
  color?: string;
  children: ReactNode;
  dot?: boolean;
  pulse?: boolean;
  className?: string;
}

/** Small tinted pill. The colour drives text, border and background so any status colour works. */
export function Badge({ color = '#94a3b8', children, dot, pulse, className }: BadgeProps) {
  return (
    <span
      className={clsx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold tracking-wide', className)}
      style={{
        color,
        background: `color-mix(in oklab, ${color} 14%, transparent)`,
        boxShadow: `inset 0 0 0 1px color-mix(in oklab, ${color} 30%, transparent)`,
      }}
    >
      {dot && (
        <span className="relative inline-flex size-1.5">
          {pulse && <span className="absolute inset-0 animate-ping rounded-full opacity-70" style={{ background: color }} />}
          <span className="relative inline-flex size-1.5 rounded-full" style={{ background: color }} />
        </span>
      )}
      {children}
    </span>
  );
}
