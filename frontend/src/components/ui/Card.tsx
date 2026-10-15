import type { HTMLAttributes, ReactNode } from 'react';
import { clsx } from 'clsx';
import SpotlightCard from '../reactbits/SpotlightCard';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Adds the ReactBits spotlight that follows the pointer, tinted with this colour. */
  spotlight?: string;
  padded?: boolean;
}

/** Glass panel. With {@code spotlight} it becomes an interactive ReactBits SpotlightCard. */
export function Card({ spotlight, padded = true, className, children, ...rest }: CardProps) {
  const classes = clsx('relative overflow-hidden rounded-3xl', padded && 'p-5 sm:p-6', className);
  if (spotlight) {
    return (
      <SpotlightCard
        className={clsx('glass', classes)}
        spotlightColor={spotlight}
        intensity={0.55}
        spotlightSize={420}
        borderGlow={0.6}
        {...rest}
      >
        {children}
      </SpotlightCard>
    );
  }
  return (
    <div className={clsx('glass', classes)} {...rest}>
      {children}
    </div>
  );
}

export function CardHeader({ title, subtitle, action, icon }: {
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="mb-4 flex items-start justify-between gap-4">
      <div className="flex min-w-0 items-start gap-3">
        {icon && <div className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl bg-white/5 text-ink-200">{icon}</div>}
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold">{title}</h3>
          {subtitle && <p className="mt-0.5 text-sm text-ink-400">{subtitle}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}
