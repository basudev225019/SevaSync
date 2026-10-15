import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Bike, Clock3, Database, FileSpreadsheet, HandHeart, Leaf, Medal, Trash2, Truck, Weight } from 'lucide-react';
import { api } from '../services/api';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { Card, CardHeader } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ErrorState, Skeleton } from '../components/ui/Feedback';
import { errorMessage } from '../lib/errors';
import { CategoryChart, TimelineChart } from '../components/charts/Charts';
import { num, num1, strategyLabel } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { RankEntry, Role } from '../types';

export default function ImpactView() {
  const impact = useQuery({ queryKey: ['impact'], queryFn: api.impact });
  const backup = useMutation({
    mutationFn: api.createBackup,
    onSuccess: (r) => toast.success(`Database snapshot saved as ${r.file}`),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const exportCsv = useMutation({
    mutationFn: api.exportCsv,
    onSuccess: (r) => toast.success(`CSV files written to data/${r.folder}`),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const i = impact.data;

  return (
    <>
      <PageHeader
        eyebrow="Impact"
        accent="#1f9d55"
        title="What SevaSync has achieved"
        description="Every figure is computed from the live data on request, never stored separately, so it can't drift."
        actions={
          <>
            <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>Export CSV</Button>
            <Button variant="secondary" icon={<Database className="size-4" />} loading={backup.isPending} onClick={() => backup.mutate()}>Snapshot DB</Button>
          </>
        }
      />
      {impact.isError && <ErrorState error={impact.error} onRetry={() => impact.refetch()} />}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Meals served" value={i?.servingsDelivered} icon={<HandHeart className="size-4" />} accent="#1f9d55" hint={i && `${num(i.deliveries)} deliveries`} />
        <StatCard label="Food rescued" value={i?.foodSavedKg} decimals={1} suffix="kg" icon={<Weight className="size-4" />} accent="#e8862a" />
        <StatCard label="CO₂e avoided" value={i?.co2AvoidedKg} decimals={1} suffix="kg" icon={<Leaf className="size-4" />} accent="#3fa66b" hint="planning estimate: 2.5 kg CO₂e per kg" />
        <StatCard label="Delivery rate" value={i?.deliveryRatePercent} decimals={1} suffix="%" icon={<Truck className="size-4" />} accent="#0f8fd6" hint="of food that finished its journey" />
        <StatCard label="Wasted" value={i?.servingsWasted} suffix="servings" icon={<Trash2 className="size-4" />} accent="#dc2f4f" hint={i && `${num1(i.foodWastedKg)} kg expired before delivery`} />
        <StatCard label="Avg. match → delivery" value={i?.averageDeliveryMinutes} suffix="min" icon={<Clock3 className="size-4" />} accent="#8b5cf6" />
        <StatCard label="Active volunteers" value={i?.volunteers} icon={<Bike className="size-4" />} accent={roleColor.VOLUNTEER} />
        <StatCard label="Partner organisations" value={i ? i.donors + i.recipients : undefined} icon={<HandHeart className="size-4" />} accent={roleColor.RECIPIENT} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Daily flow" subtitle="Last 14 days" />
          {i ? <TimelineChart data={i.timeline} height={280} /> : <Skeleton className="h-72" />}
        </Card>
        <Card>
          <CardHeader title="By food type" subtitle="Servings donated vs delivered" />
          {i ? <CategoryChart data={i.categories} height={280} /> : <Skeleton className="h-72" />}
        </Card>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Leaderboard title="Top kitchens" role="DONOR" rows={i?.topDonors} />
        <Leaderboard title="Top volunteers" role="VOLUNTEER" rows={i?.topVolunteers} showKm />
        <Leaderboard title="Recipients served" role="RECIPIENT" rows={i?.topRecipients} />
      </div>

      {i && i.strategies.length > 0 && (
        <Card className="mt-6">
          <CardHeader title="Dispatches by matching strategy" subtitle="How the live engine has been configured over time" />
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {i.strategies.map((s) => (
              <div key={s.strategy} className="rounded-2xl border hairline bg-wash/[0.02] p-4">
                <p className="text-xs uppercase tracking-[0.14em] text-ink-500">{strategyLabel[s.strategy] ?? s.strategy}</p>
                <p className="mt-1 font-display text-2xl font-semibold">{num(s.dispatches)}</p>
                <p className="text-xs text-ink-400">{num(s.servings)} servings · avg {num1(s.averageDistanceKm)} km</p>
              </div>
            ))}
          </div>
        </Card>
      )}
    </>
  );
}

function Leaderboard({ title, role, rows, showKm }: { title: string; role: Role; rows?: RankEntry[]; showKm?: boolean }) {
  const max = Math.max(1, ...(rows ?? []).map((r) => r.servings));
  const medal = ['#f2a950', '#b8a99a', '#d6a77a'];
  return (
    <Card spotlight={`${roleColor[role]}26`}>
      <CardHeader title={title} icon={<Medal className="size-4" />} />
      {!rows ? <Skeleton className="h-40" /> : rows.length === 0 ? <p className="text-sm text-ink-400">No deliveries yet.</p> : (
        <ol className="space-y-3">
          {rows.map((r, idx) => (
            <li key={r.id}>
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="flex min-w-0 items-center gap-2">
                  <span className="grid size-6 shrink-0 place-items-center rounded-full text-[11px] font-bold" style={{ color: medal[idx] ?? '#86705d', background: `${medal[idx] ?? '#86705d'}1f` }}>{idx + 1}</span>
                  <span className="truncate text-ink-200">{r.name}</span>
                </span>
                <span className="shrink-0 text-xs text-ink-400 tabular">
                  {num(r.servings)} meals{showKm && r.distanceKm != null ? ` · ${num1(r.distanceKm)} km` : ''}
                </span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-wash/6">
                <div className="h-full rounded-full" style={{ width: `${(r.servings / max) * 100}%`, background: roleColor[role] }} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
