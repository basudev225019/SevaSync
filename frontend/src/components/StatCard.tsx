import type { ReactNode } from 'react';
import { clsx } from 'clsx';
import CountUp from './reactbits/CountUp';
import { Card } from './ui/Card';

interface StatCardProps {
  label: string;
  value: number | null | undefined;
  icon: ReactNode;
  accent: string;
  suffix?: string;
  decimals?: number;
  hint?: ReactNode;
  className?: string;
}

/** KPI tile: animated count-up number (ReactBits CountUp) on a spotlight glass card. */
export function StatCard({ label, value, icon, accent, suffix, decimals = 0, hint, className }: StatCardProps) {
  return (
    <Card spotlight={`${accent}33`} className={clsx('group', className)}>
      <div className="pointer-events-none absolute -right-10 -top-10 size-36 rounded-full opacity-20 blur-3xl transition-opacity duration-500 group-hover:opacity-40" style={{ background: accent }} />
      <div className="relative flex items-start justify-between gap-3">
        <span className="text-xs font-medium uppercase tracking-[0.14em] text-ink-400">{label}</span>
        <span className="grid size-9 place-items-center rounded-xl" style={{ color: accent, background: `color-mix(in oklab, ${accent} 14%, transparent)` }}>
          {icon}
        </span>
      </div>
      <div className="relative mt-3 flex items-baseline gap-1.5 font-display text-3xl font-semibold text-ink-100 tabular sm:text-[34px]">
        {value == null ? (
          <span className="text-ink-500">–</span>
        ) : (
          <CountUp to={value} decimals={decimals} duration={1.6} separator="," trigger="load" />
        )}
        {suffix && <span className="text-base font-medium text-ink-400">{suffix}</span>}
      </div>
      {hint && <div className="relative mt-1.5 text-xs text-ink-400">{hint}</div>}
    </Card>
  );
}
