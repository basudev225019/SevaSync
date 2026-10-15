import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AnimatePresence, motion } from 'motion/react';
import { PackagePlus, Undo2, UtensilsCrossed } from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/Button';
import { Segmented } from '../components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { errorMessage } from '../lib/errors';
import { Freshness } from '../components/ui/Freshness';
import { BatchPill } from '../components/ui/StatusPill';
import { Modal } from '../components/ui/Modal';
import { DispatchList } from '../components/DispatchList';
import { CreateBatchModal } from '../components/modals/CreateBatchModal';
import { categoryEmoji, categoryLabel, dateTime, humanize, num1, timeAgo } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { Batch, BatchStatus } from '../types';

type Filter = 'OPEN' | BatchStatus | 'ALL';

export default function BatchesView() {
  const { user } = useAuth();
  const admin = user?.role === 'ADMIN';
  const [filter, setFilter] = useState<Filter>('OPEN');
  const [selected, setSelected] = useState<string | null>(null);
  const [logging, setLogging] = useState(false);
  const list = useQuery({ queryKey: ['batches', admin ? 'all' : 'mine'], queryFn: () => api.batches() });

  const items = (list.data ?? []).filter((b) =>
    filter === 'ALL' ? true : filter === 'OPEN' ? b.status === 'AVAILABLE' || b.status === 'ALLOCATED' : b.status === filter,
  );

  return (
    <>
      <PageHeader
        eyebrow={admin ? 'All food' : 'My food'}
        accent={roleColor.DONOR}
        title={admin ? 'Every batch on campus' : 'Everything you have shared'}
        description="Tap a batch to see who it went to and its complete audit history."
        actions={!admin && <Button icon={<PackagePlus className="size-4" />} onClick={() => setLogging(true)}>Log surplus</Button>}
      />
      <Segmented
        className="mb-6 max-w-2xl"
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'OPEN', label: 'Open' },
          { value: 'COMPLETED', label: 'Delivered' },
          { value: 'EXPIRED', label: 'Expired' },
          { value: 'WITHDRAWN', label: 'Withdrawn' },
          { value: 'ALL', label: 'All' },
        ]}
      />
      {list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> : list.isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-40" />)}</div>
      ) : items.length === 0 ? (
        <EmptyState icon={<UtensilsCrossed className="size-5" />} title="Nothing here">No batches match this filter.</EmptyState>
      ) : (
        <motion.div layout className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          <AnimatePresence initial={false}>
            {items.map((b) => <BatchTile key={b.id} b={b} showDonor={admin} onOpen={() => setSelected(b.id)} />)}
          </AnimatePresence>
        </motion.div>
      )}
      <BatchDetail id={selected} onClose={() => setSelected(null)} />
      <CreateBatchModal open={logging} onClose={() => setLogging(false)} />
    </>
  );
}

function BatchTile({ b, showDonor, onOpen }: { b: Batch; showDonor: boolean; onOpen: () => void }) {
  const open = b.status === 'AVAILABLE' || b.status === 'ALLOCATED';
  const matched = b.servings - b.remainingServings;
  return (
    <motion.button
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.97 }}
      onClick={onOpen}
      className="glass group rounded-3xl p-5 text-left transition hover:bg-wash/[0.05]"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid size-11 place-items-center rounded-2xl bg-wash/5 text-xl transition group-hover:scale-105">{categoryEmoji[b.category]}</span>
        <BatchPill status={b.status} />
      </div>
      <h3 className="mt-4 truncate text-base font-semibold">{b.foodName}</h3>
      <p className="mt-0.5 text-xs text-ink-400">
        {showDonor && b.donor ? `${b.donor.name} · ` : ''}{categoryLabel[b.category]} · {b.servings} servings · logged {timeAgo(b.createdAt)}
      </p>
      {open ? (
        <>
          <div className="mt-4 flex items-center justify-between text-xs text-ink-400">
            <span>{matched} of {b.servings} matched</span>
            <span>{num1(b.kg)} kg</span>
          </div>
          <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-wash/6">
            <div className="h-full rounded-full bg-sky-400" style={{ width: `${(matched / b.servings) * 100}%` }} />
          </div>
          <Freshness className="mt-3" compact expiresAt={b.expiresAt} shelfLifeMinutes={b.shelfLifeMinutes} />
        </>
      ) : (
        <p className="mt-4 text-xs text-ink-500">{b.status === 'EXPIRED' ? `${b.remainingServings} servings could not be delivered in time` : `Closed ${dateTime(b.expiresAt)}`}</p>
      )}
    </motion.button>
  );
}

function BatchDetail({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const detail = useQuery({ queryKey: ['batches', 'detail', id], queryFn: () => api.batch(id!), enabled: !!id });
  const withdraw = useMutation({
    mutationFn: () => api.withdrawBatch(id!, 'Withdrawn from the dashboard'),
    onSuccess: () => {
      toast.success('Batch withdrawn');
      for (const k of ['batches', 'network', 'dispatches']) qc.invalidateQueries({ queryKey: [k] });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const b = detail.data;
  const canWithdraw = user?.role === 'DONOR' && b && (b.status === 'AVAILABLE' || b.status === 'ALLOCATED');

  return (
    <Modal
      open={!!id}
      onClose={onClose}
      width="lg"
      title={b ? `${categoryEmoji[b.category]} ${b.foodName}` : 'Loading…'}
      subtitle={b ? `${b.id} · ${b.servings} servings · cooked ${dateTime(b.preparedAt)} · safe until ${dateTime(b.expiresAt)}` : undefined}
      footer={canWithdraw ? <Button variant="danger" icon={<Undo2 className="size-4" />} loading={withdraw.isPending} onClick={() => withdraw.mutate()}>Withdraw batch</Button> : undefined}
    >
      {detail.isError && <ErrorState error={detail.error} />}
      {b && (
        <div className="space-y-6">
          {b.notes && <p className="rounded-2xl bg-wash/[0.03] px-4 py-3 text-sm text-ink-300">“{b.notes}”</p>}
          <div>
            <h4 className="label">Where it went</h4>
            {b.dispatches && b.dispatches.length > 0 ? <DispatchList items={b.dispatches} columns={1} /> : <p className="text-sm text-ink-400">Not matched yet.</p>}
          </div>
          <div>
            <h4 className="label">Audit history</h4>
            <ol className="relative ml-2 border-l hairline">
              {(b.history ?? []).map((h) => (
                <li key={h.seq} className="relative pb-4 pl-5 last:pb-0">
                  <span className="absolute -left-[5px] top-1.5 size-2.5 rounded-full bg-saffron-400 ring-4 ring-night-850" />
                  <p className="text-sm text-ink-200">{humanize(h.action)}</p>
                  <p className="text-xs text-ink-500">{dateTime(h.timestamp)}{h.details ? ` · ${h.details}` : ''}</p>
                </li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </Modal>
  );
}
