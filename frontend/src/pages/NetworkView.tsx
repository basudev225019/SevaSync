import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { Crosshair, Eye, Layers, X } from 'lucide-react';
import { clsx } from 'clsx';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { CampusScene, type Selection } from '../components/three/CampusScene';
import { LiveFeed } from '../components/layout/LiveFeed';
import { Skeleton } from '../components/ui/Feedback';
import { Badge } from '../components/ui/Badge';
import { DispatchPill, NeedPill } from '../components/ui/StatusPill';
import { categoryEmoji, donorTypeLabel, minutesLeft, num, recipientTypeLabel, vehicleLabel } from '../lib/format';
import { dispatchColor, freshnessColor, roleColor } from '../lib/theme';
import type { Network } from '../types';

/** Full-screen live 3D map of the whole network with a details panel for whatever is clicked. */
export default function NetworkView() {
  const { user } = useAuth();
  const network = useQuery({ queryKey: ['network', 'full'], queryFn: api.network, refetchInterval: 10_000 });
  const [selected, setSelected] = useState<Selection>(null);
  const [focusMine, setFocusMine] = useState(false);
  const [labels, setLabels] = useState(true);
  const n = network.data;
  const canFocus = user && user.role !== 'ADMIN';

  return (
    <div className="relative h-[calc(100dvh-4rem)] overflow-hidden">
      {n ? (
        <CampusScene
          network={n}
          className="absolute inset-0"
          selected={selected}
          onSelect={setSelected}
          focusId={focusMine ? user?.id : null}
          showLabels={labels}
        />
      ) : (
        <Skeleton className="absolute inset-0 rounded-none" />
      )}

      {/* header + legend */}
      <div className="pointer-events-none absolute left-4 top-4 max-w-sm sm:left-6 sm:top-6">
        <h1 className="font-display text-2xl font-semibold tracking-tight">Live network</h1>
        <p className="mt-1 text-sm text-ink-400">Drag to orbit, scroll to zoom, click anything for details.</p>
        <div className="pointer-events-auto mt-4 flex flex-wrap gap-2">
          <Legend color={roleColor.DONOR} label="Kitchens" />
          <Legend color={roleColor.RECIPIENT} label="Recipients" />
          <Legend color={roleColor.VOLUNTEER} label="Volunteers" />
          <Legend color={dispatchColor.ASSIGNED} label="Awaiting volunteer" dashed />
          <Legend color="#34d399" label="Recently delivered" />
        </div>
      </div>

      {/* controls + counters */}
      <div className="absolute right-4 top-4 flex flex-col items-end gap-2 sm:right-6 sm:top-6">
        <div className="glass flex gap-1 rounded-2xl p-1">
          {canFocus && (
            <ToolButton active={focusMine} onClick={() => setFocusMine((f) => !f)} icon={<Crosshair className="size-4" />} label="My links" />
          )}
          <ToolButton active={labels} onClick={() => setLabels((l) => !l)} icon={<Layers className="size-4" />} label="Labels" />
        </div>
        {n && <Counters n={n} />}
      </div>

      {/* details of the selection */}
      <AnimatePresence>
        {n && selected && (
          <motion.aside
            key={selected.id}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            className="glass-strong absolute bottom-4 left-4 w-[min(360px,calc(100vw-32px))] rounded-3xl p-5 sm:bottom-6 sm:left-6"
          >
            <button onClick={() => setSelected(null)} className="absolute right-3 top-3 grid size-8 place-items-center rounded-xl text-ink-400 hover:bg-white/5" aria-label="Close details">
              <X className="size-4" />
            </button>
            <SelectionDetails n={n} s={selected} />
          </motion.aside>
        )}
      </AnimatePresence>

      {/* live feed */}
      <div className="glass absolute bottom-6 right-6 hidden w-80 rounded-3xl p-3 xl:block">
        <p className="flex items-center gap-2 px-3 pb-2 pt-1 text-xs font-semibold uppercase tracking-[0.16em] text-ink-400"><Eye className="size-3.5" /> Happening now</p>
        <div className="max-h-72 overflow-y-auto pr-1"><LiveFeed limit={10} compact /></div>
      </div>
    </div>
  );
}

function Legend({ color, label, dashed }: { color: string; label: string; dashed?: boolean }) {
  return (
    <span className="glass inline-flex items-center gap-2 rounded-full px-3 py-1 text-[11px] text-ink-300">
      <span className={clsx('h-0.5 w-4 rounded-full', dashed && 'bg-transparent')} style={dashed ? { borderTop: `2px dashed ${color}` } : { background: color, height: 8, width: 8 }} />
      {label}
    </span>
  );
}

function ToolButton({ active, onClick, icon, label }: { active: boolean; onClick: () => void; icon: React.ReactNode; label: string }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={clsx('flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold transition', active ? 'bg-white/10 text-ink-100' : 'text-ink-400 hover:text-ink-200')}
    >
      {icon} {label}
    </button>
  );
}

function Counters({ n }: { n: Network }) {
  const moving = n.routes.filter((r) => r.status === 'CLAIMED' || r.status === 'PICKED_UP').length;
  const waiting = n.routes.filter((r) => r.status === 'ASSIGNED').length;
  const servings = n.batches.reduce((s, b) => s + b.remainingServings, 0);
  return (
    <div className="glass grid grid-cols-3 gap-4 rounded-2xl px-4 py-3 text-center">
      <Counter value={moving} label="moving" color={roleColor.VOLUNTEER} />
      <Counter value={waiting} label="need a rider" color={dispatchColor.ASSIGNED} />
      <Counter value={servings} label="unmatched" color={roleColor.DONOR} />
    </div>
  );
}

function Counter({ value, label, color }: { value: number; label: string; color: string }) {
  return (
    <div>
      <div className="font-display text-xl font-semibold tabular" style={{ color }}>{num(value)}</div>
      <div className="text-[10px] uppercase tracking-wider text-ink-500">{label}</div>
    </div>
  );
}

function SelectionDetails({ n, s }: { n: Network; s: NonNullable<Selection> }) {
  const lookups = useMemo(() => ({
    donors: new Map(n.donors.map((d) => [d.id, d])),
    recipients: new Map(n.recipients.map((r) => [r.id, r])),
    volunteers: new Map(n.volunteers.map((v) => [v.id, v])),
  }), [n]);

  if (s.kind === 'donor') {
    const d = lookups.donors.get(s.id);
    if (!d) return null;
    const food = n.batches.filter((b) => b.donorId === d.id);
    return (
      <>
        <Badge color={roleColor.DONOR}>{d.donorType ? donorTypeLabel[d.donorType] : 'Kitchen'}</Badge>
        <h3 className="mt-3 text-lg font-semibold">{d.name}</h3>
        <p className="text-sm text-ink-400">{d.servingsAvailable} servings waiting · {food.length} open batch{food.length === 1 ? '' : 'es'}</p>
        <ul className="mt-4 space-y-2">
          {food.map((b) => (
            <li key={b.id} className="flex items-center gap-3 rounded-2xl bg-white/[0.03] px-3 py-2">
              <span className="text-lg">{categoryEmoji[b.category]}</span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-ink-100">{b.foodName}</p>
                <p className="text-xs text-ink-500">{b.remainingServings}/{b.servings} unmatched</p>
              </div>
              <span className="text-xs font-semibold tabular" style={{ color: freshnessColor(b.freshness) }}>{minutesLeft(b.minutesLeft)}</span>
            </li>
          ))}
        </ul>
      </>
    );
  }
  if (s.kind === 'recipient') {
    const r = lookups.recipients.get(s.id);
    if (!r) return null;
    const incoming = n.routes.filter((x) => x.recipientId === r.id);
    return (
      <>
        <div className="flex flex-wrap gap-2">
          <Badge color={roleColor.RECIPIENT}>{r.recipientType ? recipientTypeLabel[r.recipientType] : 'Recipient'}</Badge>
          {r.needLevel && <NeedPill level={r.needLevel} />}
        </div>
        <h3 className="mt-3 text-lg font-semibold">{r.name}</h3>
        <p className="text-sm text-ink-400">Room for {r.remainingToday} of {r.dailyCapacity} servings today</p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-white/6">
          <div className="h-full rounded-full bg-rose-400" style={{ width: `${(1 - r.remainingToday / (r.dailyCapacity || 1)) * 100}%` }} />
        </div>
        <p className="label mt-5">Incoming</p>
        {incoming.length === 0 ? <p className="text-sm text-ink-500">Nothing on the way.</p> : (
          <ul className="space-y-2">
            {incoming.map((x) => (
              <li key={x.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate text-ink-200">{x.servings} × {x.foodName}</span>
                <DispatchPill status={x.status} />
              </li>
            ))}
          </ul>
        )}
      </>
    );
  }
  if (s.kind === 'volunteer') {
    const v = lookups.volunteers.get(s.id);
    if (!v) return null;
    return (
      <>
        <Badge color={roleColor.VOLUNTEER}>{v.vehicle ? vehicleLabel[v.vehicle] : 'Volunteer'}</Badge>
        <h3 className="mt-3 text-lg font-semibold">{v.name}</h3>
        <p className="text-sm text-ink-400">{v.activePickups} active pickup{v.activePickups === 1 ? '' : 's'}</p>
      </>
    );
  }
  const r = [...n.routes, ...n.recentDeliveries].find((x) => x.id === s.id);
  if (!r) return null;
  return (
    <>
      <DispatchPill status={r.status} />
      <h3 className="mt-3 text-lg font-semibold">{r.servings} × {r.foodName}</h3>
      <p className="text-sm text-ink-400">
        {lookups.donors.get(r.donorId)?.name ?? r.donorId} → {lookups.recipients.get(r.recipientId)?.name ?? r.recipientId} · {r.distanceKm} km
      </p>
      {r.volunteerId && (
        <p className="mt-2 text-sm text-sky-300">Carried by {lookups.volunteers.get(r.volunteerId)?.name ?? 'a volunteer'}</p>
      )}
    </>
  );
}
