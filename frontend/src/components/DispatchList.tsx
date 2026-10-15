import { useState } from 'react';
import { AnimatePresence } from 'motion/react';
import { useDispatchActions } from '../hooks/useDispatchActions';
import type { Dispatch } from '../types';
import { DispatchCard } from './DispatchCard';
import { DeliverModal } from './modals/DeliverModal';
import { CancelModal } from './modals/CancelModal';

/** Grid of dispatch cards with all actions and their dialogs wired up. */
export function DispatchList({ items, columns = 2 }: { items: Dispatch[]; columns?: 1 | 2 | 3 }) {
  const { step, confirm, cancel } = useDispatchActions();
  const [confirming, setConfirming] = useState<Dispatch | null>(null);
  const [cancelling, setCancelling] = useState<Dispatch | null>(null);
  const busyId = step.isPending ? step.variables?.id ?? null : null;
  const cols = columns === 1 ? '' : columns === 2 ? 'lg:grid-cols-2' : 'lg:grid-cols-2 2xl:grid-cols-3';

  return (
    <>
      <div className={`grid gap-4 ${cols}`}>
        <AnimatePresence initial={false}>
          {items.map((d) => (
            <DispatchCard
              key={d.id}
              d={d}
              busyId={busyId}
              onStep={(x, action) => step.mutate({ id: x.id, action })}
              onConfirm={setConfirming}
              onCancel={setCancelling}
            />
          ))}
        </AnimatePresence>
      </div>
      <DeliverModal
        dispatch={confirming}
        busy={confirm.isPending}
        onClose={() => setConfirming(null)}
        onConfirm={(rating, feedback) =>
          confirming && confirm.mutate({ id: confirming.id, rating, feedback }, { onSuccess: () => setConfirming(null) })
        }
      />
      <CancelModal
        dispatch={cancelling}
        busy={cancel.isPending}
        onClose={() => setCancelling(null)}
        onCancel={(reason) => cancelling && cancel.mutate({ id: cancelling.id, reason }, { onSuccess: () => setCancelling(null) })}
      />
    </>
  );
}
