import { motion } from 'motion/react';
import { clsx } from 'clsx';
import { ArrowRight, Bike, CircleCheck, MapPin, Navigation, Star, Truck, Undo2, X } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { categoryEmoji, categoryLabel, clock, num1, strategyLabel, vehicleLabel } from '../lib/format';
import { dispatchColor, roleColor } from '../lib/theme';
import type { Dispatch, DispatchStatus } from '../types';
import { Button } from './ui/Button';
import { Freshness } from './ui/Freshness';
import { DispatchPill } from './ui/StatusPill';

const STAGES: { status: DispatchStatus; label: string; at: (d: Dispatch) => string | null }[] = [
  { status: 'ASSIGNED', label: 'Matched', at: (d) => d.assignedAt },
  { status: 'CLAIMED', label: 'Claimed', at: (d) => d.claimedAt },
  { status: 'PICKED_UP', label: 'Picked up', at: (d) => d.pickedUpAt },
  { status: 'DELIVERED', label: 'Delivered', at: (d) => d.deliveredAt },
];
const ORDER: DispatchStatus[] = ['ASSIGNED', 'CLAIMED', 'PICKED_UP', 'DELIVERED'];

export interface DispatchCardActions {
  onStep?: (d: Dispatch, action: 'claim' | 'release' | 'pickup' | 'deliver') => void;
  onConfirm?: (d: Dispatch) => void;
  onCancel?: (d: Dispatch) => void;
  busyId?: string | null;
}

/**
 * One hand-over, from kitchen to shelter. The buttons come straight from the server's {@code allowedActions}
 * (the dispatch State pattern) filtered by what the viewer's role may do, so the UI can never offer an illegal step.
 */
export function DispatchCard({ d, onStep, onConfirm, onCancel, busyId }: { d: Dispatch } & DispatchCardActions) {
  const { user } = useAuth();
  const role = user?.role;
  const mine = d.volunteerId === user?.id;
  const allowed = new Set(d.allowedActions);
  const busy = busyId === d.id;
  const stageIndex = d.status === 'CANCELLED' ? -1 : ORDER.indexOf(d.status);
  const live = d.status === 'CLAIMED' || d.status === 'PICKED_UP';
  const b = d.batch;

  const buttons: React.ReactNode[] = [];
  if (role === 'VOLUNTEER' && onStep) {
    if (allowed.has('claim')) {
      buttons.push(
        <Button key="claim" size="sm" loading={busy} disabled={d.fitsVehicle === false} icon={<Bike className="size-3.5" />} onClick={() => onStep(d, 'claim')}>
          {d.fitsVehicle === false ? 'Too big for your vehicle' : 'Claim pickup'}
        </Button>,
      );
    }
    if (mine && allowed.has('pickup')) {
      buttons.push(<Button key="pickup" size="sm" loading={busy} icon={<Truck className="size-3.5" />} onClick={() => onStep(d, 'pickup')}>I've picked it up</Button>);
    }
    if (mine && allowed.has('deliver')) {
      buttons.push(<Button key="deliver" size="sm" variant="success" loading={busy} icon={<CircleCheck className="size-3.5" />} onClick={() => onStep(d, 'deliver')}>Mark delivered</Button>);
    }
    if (mine && allowed.has('release')) {
      buttons.push(<Button key="release" size="sm" variant="ghost" disabled={busy} icon={<Undo2 className="size-3.5" />} onClick={() => onStep(d, 'release')}>Release</Button>);
    }
  }
  if (role === 'RECIPIENT' && onConfirm && allowed.has('confirm') && !d.confirmedAt) {
    buttons.push(<Button key="confirm" size="sm" loading={busy} icon={<Star className="size-3.5" />} onClick={() => onConfirm(d)}>Confirm & rate</Button>);
  }
  if (role === 'ADMIN' && onCancel && allowed.has('cancel')) {
    buttons.push(<Button key="cancel" size="sm" variant="danger" disabled={busy} icon={<X className="size-3.5" />} onClick={() => onCancel(d)}>Cancel</Button>);
  }

  return (
    <motion.article
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={clsx('glass relative overflow-hidden rounded-3xl p-5', live && 'ring-1')}
      style={live ? { ['--tw-ring-color' as string]: `${dispatchColor[d.status]}40` } : undefined}
    >
      {live && <div className="pointer-events-none absolute -right-16 -top-16 size-40 rounded-full opacity-20 blur-3xl" style={{ background: dispatchColor[d.status] }} />}

      <div className="relative flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-wash/5 text-xl">{b ? categoryEmoji[b.category] : '🍽️'}</span>
          <div className="min-w-0">
            <h3 className="truncate text-[15px] font-semibold">{b?.foodName ?? d.batchId}</h3>
            <p className="text-xs text-ink-400">
              {d.servings} servings{b && ` · ${categoryLabel[b.category]}`}{b?.vegetarian && ' · veg'} · <span className="font-mono">{d.id}</span>
            </p>
          </div>
        </div>
        <DispatchPill status={d.status} />
      </div>

      {/* route */}
      <div className="relative mt-4 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        <span className="flex min-w-0 max-w-full items-center gap-1.5">
          <span className="size-2 shrink-0 rounded-full" style={{ background: roleColor.DONOR }} />
          <span className="truncate text-ink-200">{d.donor?.name ?? 'Kitchen'}</span>
        </span>
        <span className="flex shrink-0 items-center gap-1 text-ink-500">
          <span className="h-px w-6 bg-gradient-to-r from-saffron-400/60 to-rose-400/60" />
          <ArrowRight className="size-3.5" />
          <span className="text-[11px]">{num1(d.distanceKm)} km</span>
        </span>
        <span className="flex min-w-0 max-w-full items-center gap-1.5">
          <span className="size-2 shrink-0 rounded-full" style={{ background: roleColor.RECIPIENT }} />
          <span className="truncate text-ink-200">{d.recipient?.name ?? d.recipientId}</span>
        </span>
      </div>

      {role === 'VOLUNTEER' && d.distanceToPickupKm != null && d.status === 'ASSIGNED' && (
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          <span className="inline-flex items-center gap-1 rounded-full bg-sky-400/10 px-2.5 py-1 text-sky-700"><MapPin className="size-3" /> {num1(d.distanceToPickupKm)} km to pickup</span>
          <span className="inline-flex items-center gap-1 rounded-full bg-wash/5 px-2.5 py-1 text-ink-300"><Navigation className="size-3" /> ~{d.etaMinutes} min door to door</span>
        </div>
      )}

      {b && (d.status === 'ASSIGNED' || live) && (
        <Freshness className="mt-4" expiresAt={b.expiresAt} shelfLifeMinutes={b.shelfLifeMinutes} />
      )}

      {/* progress through the life-cycle */}
      {stageIndex >= 0 && (
        <ol className="mt-4 grid grid-cols-4 gap-1.5">
          {STAGES.map((s, i) => {
            const done = i <= stageIndex;
            return (
              <li key={s.status} className="min-w-0">
                <div className="h-1 rounded-full transition-colors duration-500" style={{ background: done ? dispatchColor[ORDER[Math.min(stageIndex, 3)]] : 'rgb(107 63 29 / 0.1)' }} />
                <p className={clsx('mt-1.5 truncate text-[10px] font-medium uppercase tracking-wider', done ? 'text-ink-300' : 'text-ink-500')}>{s.label}</p>
                <p className="text-[10px] text-ink-500 tabular">{clock(s.at(d))}</p>
              </li>
            );
          })}
        </ol>
      )}
      {d.status === 'CANCELLED' && d.note && <p className="mt-3 text-xs text-ink-400">Cancelled: {d.note}</p>}

      <div className="relative mt-4 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-xs text-ink-500">
          {d.volunteer && (
            <span className="inline-flex items-center gap-1.5 text-ink-300">
              <Bike className="size-3.5 text-sky-700" /> {d.volunteer.name}{d.volunteer.vehicle && ` · ${vehicleLabel[d.volunteer.vehicle]}`}
            </span>
          )}
          {d.rating ? (
            <span className="inline-flex items-center gap-0.5 text-saffron-600">
              {Array.from({ length: d.rating }, (_, i) => <Star key={i} className="size-3 fill-current" />)}
            </span>
          ) : null}
          {!d.volunteer && <span>{strategyLabel[d.strategy] ?? d.strategy} match</span>}
        </div>
        {buttons.length > 0 && <div className="flex flex-wrap gap-2">{buttons}</div>}
      </div>
      {d.feedback && <p className="mt-3 rounded-xl bg-wash/[0.03] px-3 py-2 text-xs italic text-ink-300">“{d.feedback}”</p>}
    </motion.article>
  );
}
