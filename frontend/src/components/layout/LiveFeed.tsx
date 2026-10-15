import { AnimatePresence, motion } from 'motion/react';
import {
  Ban,
  Bike,
  CheckCircle2,
  CircleAlert,
  HeartHandshake,
  PackagePlus,
  Radio,
  Sparkles,
  Star,
  Truck,
  UserCheck,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useLive } from '../../context/LiveContext';
import { useNow } from '../../hooks/useNow';
import { timeAgo } from '../../lib/format';
import type { LiveEvent } from '../../types';

const ICON: Record<string, { icon: ReactNode; color: string }> = {
  BATCH_LOGGED: { icon: <PackagePlus className="size-4" />, color: '#fbbf24' },
  BATCH_EXPIRED: { icon: <CircleAlert className="size-4" />, color: '#f43f5e' },
  BATCH_WITHDRAWN: { icon: <Ban className="size-4" />, color: '#64748b' },
  DISPATCH_ASSIGNED: { icon: <Sparkles className="size-4" />, color: '#a78bfa' },
  DISPATCH_CLAIMED: { icon: <Bike className="size-4" />, color: '#38bdf8' },
  DISPATCH_RELEASED: { icon: <Bike className="size-4" />, color: '#64748b' },
  DISPATCH_PICKED_UP: { icon: <Truck className="size-4" />, color: '#fbbf24' },
  DISPATCH_DELIVERED: { icon: <CheckCircle2 className="size-4" />, color: '#34d399' },
  DISPATCH_CONFIRMED: { icon: <Star className="size-4" />, color: '#fcd581' },
  DISPATCH_CANCELLED: { icon: <Ban className="size-4" />, color: '#f43f5e' },
  MATCHING_RUN: { icon: <HeartHandshake className="size-4" />, color: '#a78bfa' },
  USER_REGISTERED: { icon: <UserCheck className="size-4" />, color: '#38bdf8' },
  USER_STATUS_CHANGED: { icon: <UserCheck className="size-4" />, color: '#34d399' },
};

/** Real-time activity stream (Server-Sent Events), newest first, animated in with Motion. */
export function LiveFeed({ limit = 30, compact }: { limit?: number; compact?: boolean }) {
  const { events, state } = useLive();
  const now = useNow(20_000);
  const items = events.slice(0, limit);

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 px-4 py-10 text-center text-sm text-ink-400">
        <Radio className="size-5 text-ink-500" />
        {state === 'live' ? 'Listening… activity will appear here the moment it happens.' : 'Connecting to the live feed…'}
      </div>
    );
  }

  return (
    <ul className="flex flex-col gap-1.5">
      <AnimatePresence initial={false}>
        {items.map((e) => (
          <motion.li
            key={e.id}
            layout
            initial={{ opacity: 0, y: -14, scale: 0.97, filter: 'blur(6px)' }}
            animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)' }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 420, damping: 34 }}
          >
            <FeedItem e={e} now={now} compact={compact} />
          </motion.li>
        ))}
      </AnimatePresence>
    </ul>
  );
}

function FeedItem({ e, now, compact }: { e: LiveEvent; now: number; compact?: boolean }) {
  const meta = ICON[e.type] ?? { icon: <Radio className="size-4" />, color: '#94a3b8' };
  return (
    <div className="flex items-start gap-3 rounded-2xl px-3 py-2.5 transition hover:bg-white/[0.03]">
      <span
        className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-xl"
        style={{ color: meta.color, background: `color-mix(in oklab, ${meta.color} 14%, transparent)` }}
      >
        {meta.icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className={compact ? 'truncate text-[13px] text-ink-200' : 'text-[13px] leading-snug text-ink-200'}>{e.message}</p>
        <p className="mt-0.5 text-[11px] text-ink-500">{timeAgo(e.at, now)}</p>
      </div>
    </div>
  );
}
