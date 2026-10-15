import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { clsx } from 'clsx';
import { Fingerprint, Link2, Search, ShieldAlert, ShieldCheck } from 'lucide-react';
import { api } from '../services/api';
import { PageHeader } from '../components/PageHeader';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ErrorState, Skeleton } from '../components/ui/Feedback';
import DecryptedText from '../components/reactbits/DecryptedText';
import { dateTime, humanize, num } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { AuditEntry } from '../types';

const ACTION_COLOR = (a: string) =>
  a.includes('DELIVERED') || a.includes('CONFIRMED') ? '#1f9d55'
    : a.includes('EXPIRED') || a.includes('CANCELLED') || a.includes('WITHDRAWN') ? '#dc2f4f'
      : a.includes('BATCH') ? '#e8862a'
        : a.includes('DISPATCH') ? '#0f8fd6'
          : a.includes('USER') || a.includes('PASSWORD') ? '#8b5cf6' : '#a08c7a';

export default function AuditView() {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState<number | null>(null);
  const log = useQuery({ queryKey: ['audit'], queryFn: () => api.audit(300) });
  const verify = useQuery({ queryKey: ['audit-verify'], queryFn: api.verifyAudit });

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    const all = [...(log.data ?? [])].reverse();
    return q ? all.filter((e) => `${e.action} ${e.entityId} ${e.details} ${e.actorId}`.toLowerCase().includes(q)) : all;
  }, [log.data, query]);

  const v = verify.data;
  return (
    <>
      <PageHeader
        eyebrow="Audit trail"
        accent={roleColor.ADMIN}
        title="Tamper-evident history"
        description="Every action appends a row whose HMAC-SHA-256 covers its content and the previous row's MAC, keyed with a secret held only by the server."
        actions={<Button variant="secondary" icon={<ShieldCheck className="size-4" />} loading={verify.isFetching} onClick={() => verify.refetch()}>Verify chain now</Button>}
      />

      <Card spotlight={v?.valid === false ? '#dc2f4f44' : '#1f9d5533'} className="mb-6">
        {verify.isError ? <ErrorState error={verify.error} /> : !v ? <Skeleton className="h-20" /> : (
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
            <span className={clsx('grid size-14 shrink-0 place-items-center rounded-2xl', v.valid ? 'bg-leaf-500/15 text-leaf-700' : 'bg-rose-500/15 text-rose-700')}>
              {v.valid ? <ShieldCheck className="size-7" /> : <ShieldAlert className="size-7" />}
            </span>
            <div className="min-w-0 flex-1">
              <p className={clsx('font-display text-2xl font-semibold', v.valid ? 'text-leaf-700' : 'text-rose-700')}>
                {v.valid ? 'Chain intact' : `Tampering detected at entry #${v.firstBadSeq}`}
              </p>
              <p className="text-sm text-ink-400">{num(v.entries)} entries recomputed · {v.reason}</p>
            </div>
            {log.data?.length ? (
              <div className="min-w-0 max-w-full font-mono text-xs text-ink-400 sm:max-w-sm">
                <span className="text-ink-500">head </span>
                <DecryptedText key={log.data[log.data.length - 1].hash} text={log.data[log.data.length - 1].hash} animateOn="view" sequential speed={12} className="text-ink-200" parentClassName="block! max-w-full truncate whitespace-nowrap!" encryptedClassName="text-violet-700/70" />
              </div>
            ) : null}
          </div>
        )}
      </Card>

      <div className="relative mb-4 max-w-md">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-ink-500" />
        <input className="field pl-10" placeholder="Filter by action, id, actor or detail…" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Filter audit entries" />
      </div>

      {log.isError ? <ErrorState error={log.error} onRetry={() => log.refetch()} /> : log.isLoading ? <Skeleton className="h-96" /> : (
        <Card padded={false}>
          <ol className="divide-y divide-wash/5">
            {rows.map((e) => (
              <AuditRow key={e.seq} e={e} open={open === e.seq} onToggle={() => setOpen(open === e.seq ? null : e.seq)} bad={v?.firstBadSeq === e.seq} />
            ))}
          </ol>
        </Card>
      )}
    </>
  );
}

function AuditRow({ e, open, onToggle, bad }: { e: AuditEntry; open: boolean; onToggle: () => void; bad: boolean }) {
  const color = ACTION_COLOR(e.action);
  return (
    <li className={clsx(bad && 'bg-rose-500/10')}>
      <button onClick={onToggle} className="flex w-full items-center gap-4 px-5 py-3 text-left transition hover:bg-wash/[0.02]" aria-expanded={open}>
        <span className="w-12 shrink-0 font-mono text-xs text-ink-500">#{e.seq}</span>
        <span className="size-2 shrink-0 rounded-full" style={{ background: color, boxShadow: `0 0 10px ${color}` }} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm text-ink-100">{humanize(e.action)} <span className="text-ink-500">· {e.entityType} {e.entityId}</span></span>
          {e.details && <span className="block truncate text-xs text-ink-400">{e.details}</span>}
        </span>
        <span className="hidden w-24 shrink-0 truncate text-right text-xs text-ink-500 sm:block">{e.actorId}</span>
        <span className="hidden w-32 shrink-0 text-right text-xs text-ink-500 tabular md:block">{dateTime(e.timestamp)}</span>
        <Fingerprint className="size-4 shrink-0 text-ink-500" />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
            <div className="space-y-2 px-5 pb-4 pl-[5.25rem] font-mono text-[11px]">
              <p className="flex items-center gap-2 text-ink-500"><Link2 className="size-3" /> previous <span className="truncate text-ink-300">{e.prevHash}</span></p>
              <p className="flex items-center gap-2 text-ink-500">
                <Fingerprint className="size-3" /> this row
                <DecryptedText text={e.hash} animateOn="view" sequential speed={10} className="text-leaf-700" parentClassName="min-w-0 truncate" encryptedClassName="text-saffron-600/70" />
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </li>
  );
}
