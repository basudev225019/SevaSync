import { clsx } from 'clsx';
import { minutesLeft, minutesUntil } from '../../lib/format';
import { freshnessColor } from '../../lib/theme';
import { useNow } from '../../hooks/useNow';

/** Live countdown + colour bar for how much safe consumption time a batch has left. */
export function Freshness({ expiresAt, shelfLifeMinutes, compact, className }: {
  expiresAt: string;
  shelfLifeMinutes: number;
  compact?: boolean;
  className?: string;
}) {
  const now = useNow(15_000);
  const left = Math.max(0, minutesUntil(expiresAt, now));
  const ratio = Math.max(0, Math.min(1, left / shelfLifeMinutes));
  const color = freshnessColor(ratio);
  return (
    <div className={clsx('min-w-0', className)}>
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="tabular font-semibold" style={{ color }}>{minutesLeft(left)}</span>
        {!compact && <span className="text-ink-500">left to eat safely</span>}
      </div>
      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-wash/6">
        <div
          className="h-full rounded-full transition-[width] duration-700"
          style={{ width: `${ratio * 100}%`, background: `linear-gradient(90deg, ${color}aa, ${color})`, boxShadow: `0 0 12px ${color}88` }}
        />
      </div>
    </div>
  );
}
