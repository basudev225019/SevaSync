import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowRight,
  Bike,
  BrainCircuit,
  CheckCircle2,
  Clock3,
  Gauge,
  Globe2,
  HandHeart,
  Hourglass,
  Leaf,
  PackagePlus,
  Route as RouteIcon,
  ShieldCheck,
  Star,
  Truck,
  UserCheck,
  UtensilsCrossed,
  Weight,
} from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { StatCard } from '../components/StatCard';
import { PageHeader } from '../components/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { errorMessage } from '../lib/errors';
import { Freshness } from '../components/ui/Freshness';
import { BatchPill } from '../components/ui/StatusPill';
import { DispatchList } from '../components/DispatchList';
import { TimelineChart } from '../components/charts/Charts';
import { CampusScene } from '../components/three/CampusScene';
import { LiveFeed } from '../components/layout/LiveFeed';
import { CreateBatchModal } from '../components/modals/CreateBatchModal';
import StarBorder from '../components/reactbits/StarBorder';
import { categoryEmoji, num, num1, roleLabel } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { PersonalImpact, User } from '../types';

function greeting(): string {
  const h = new Date().getHours();
  return h < 5 ? 'Working late' : h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export default function Dashboard() {
  const { user } = useAuth();
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, refetchInterval: 60_000 });
  if (!user) return null;
  if (user.status === 'PENDING') return <PendingHome user={user} />;
  const impact = me.data?.impact;
  switch (user.role) {
    case 'DONOR':
      return <DonorHome user={user} impact={impact} />;
    case 'VOLUNTEER':
      return <VolunteerHome user={user} impact={impact} />;
    case 'RECIPIENT':
      return <RecipientHome user={user} impact={impact} />;
    default:
      return <AdminHome user={user} />;
  }
}

// ------------------------------------------------------------------ donor

function DonorHome({ user, impact }: { user: User; impact?: PersonalImpact }) {
  const [logging, setLogging] = useState(false);
  const batches = useQuery({ queryKey: ['batches', 'mine'], queryFn: () => api.batches() });
  const dispatches = useQuery({ queryKey: ['dispatches', 'mine'], queryFn: () => api.dispatches() });
  const open = (batches.data ?? []).filter((b) => b.status === 'AVAILABLE' || b.status === 'ALLOCATED');
  const moving = (dispatches.data ?? []).filter((d) => d.status === 'CLAIMED' || d.status === 'PICKED_UP');

  return (
    <>
      <PageHeader
        eyebrow={roleLabel.DONOR}
        accent={roleColor.DONOR}
        title={`${greeting()}, ${user.name}`}
        description="Log what is left over and SevaSync takes it from there: safety check, matching and a volunteer at your door."
        actions={
          <button onClick={() => setLogging(true)}>
            <StarBorder as="span" theme="light" color="#e8862a" duration={4} backgroundColor="#fffaf2" textColor="#2a1a10" className="inline-block">
              <span className="flex items-center gap-2 font-semibold"><PackagePlus className="size-4" /> Log surplus</span>
            </StarBorder>
          </button>
        }
      />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Meals delivered" value={impact?.servingsDelivered} icon={<HandHeart className="size-4" />} accent="#1f9d55" />
        <StatCard label="Food rescued" value={impact?.foodSavedKg} decimals={1} suffix="kg" icon={<Weight className="size-4" />} accent="#e8862a" />
        <StatCard label="CO₂e avoided" value={impact?.co2AvoidedKg} decimals={1} suffix="kg" icon={<Leaf className="size-4" />} accent="#3fa66b" />
        <StatCard label="Avg. rating" value={impact?.averageRating ?? null} decimals={1} suffix="/ 5" icon={<Star className="size-4" />} accent="#f2a950" />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-5">
        <Card className="xl:col-span-3">
          <CardHeader title="Your food right now" subtitle="Live freshness for everything not yet delivered" icon={<UtensilsCrossed className="size-4" />} action={<Link to="/app/batches"><Button size="sm" variant="ghost" trailing={<ArrowRight className="size-3.5" />}>All</Button></Link>} />
          {batches.isLoading ? <Skeleton className="h-40" /> : open.length === 0 ? (
            <EmptyState icon={<PackagePlus className="size-5" />} title="Nothing waiting" action={<Button size="sm" onClick={() => setLogging(true)}>Log surplus</Button>}>
              When the evening service ends, log what is left and it will be matched within seconds.
            </EmptyState>
          ) : (
            <ul className="space-y-3">
              {open.map((b) => (
                <li key={b.id} className="flex items-center gap-4 rounded-2xl border hairline bg-wash/[0.02] p-3.5">
                  <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-wash/5 text-lg">{categoryEmoji[b.category]}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate font-semibold text-ink-100">{b.foodName}</p>
                      <BatchPill status={b.status} />
                    </div>
                    <p className="text-xs text-ink-400">{b.servings - b.remainingServings} of {b.servings} servings matched</p>
                    <Freshness className="mt-2" compact expiresAt={b.expiresAt} shelfLifeMinutes={b.shelfLifeMinutes} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="xl:col-span-2">
          <CardHeader title="Last two weeks" subtitle="Your servings donated, delivered and wasted" />
          {impact?.timeline ? <TimelineChart data={impact.timeline} height={230} /> : <Skeleton className="h-56" />}
        </Card>
      </div>

      <section className="mt-8">
        <h2 className="mb-4 text-lg font-semibold">On the move</h2>
        {moving.length === 0 ? (
          <p className="text-sm text-ink-400">No volunteer is carrying your food right now.</p>
        ) : (
          <DispatchList items={moving} />
        )}
      </section>
      <CreateBatchModal open={logging} onClose={() => setLogging(false)} />
    </>
  );
}

// ------------------------------------------------------------------ volunteer

function VolunteerHome({ user, impact }: { user: User; impact?: PersonalImpact }) {
  const mine = useQuery({ queryKey: ['dispatches', 'mine'], queryFn: () => api.dispatches() });
  const open = useQuery({ queryKey: ['pickups'], queryFn: api.openPickups, refetchInterval: 30_000 });
  const active = (mine.data ?? []).filter((d) => d.status === 'CLAIMED' || d.status === 'PICKED_UP');
  const nearest = (open.data ?? []).slice(0, 4);

  return (
    <>
      <PageHeader
        eyebrow={roleLabel.VOLUNTEER}
        accent={roleColor.VOLUNTEER}
        title={`${greeting()}, ${user.name.split(' ')[0]}`}
        description="Pick up food from campus kitchens and carry it to people who need it, before it expires."
        actions={<Link to="/app/pickups"><Button icon={<Bike className="size-4" />}>Find pickups</Button></Link>}
      />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Deliveries" value={impact?.deliveries} icon={<CheckCircle2 className="size-4" />} accent="#1f9d55" />
        <StatCard label="Meals carried" value={impact?.servingsDelivered} icon={<HandHeart className="size-4" />} accent="#0f8fd6" />
        <StatCard label="Distance" value={impact?.distanceKm} decimals={1} suffix="km" icon={<RouteIcon className="size-4" />} accent="#8b5cf6" />
        <StatCard label="CO₂e avoided" value={impact?.co2AvoidedKg} decimals={1} suffix="kg" icon={<Leaf className="size-4" />} accent="#3fa66b" />
      </div>

      <section className="mt-8">
        <h2 className="mb-4 text-lg font-semibold">Your active runs</h2>
        {mine.isLoading ? <Skeleton className="h-48" /> : active.length === 0 ? (
          <EmptyState icon={<Bike className="size-5" />} title="You're free right now" action={<Link to="/app/pickups"><Button size="sm">Browse open pickups</Button></Link>}>
            Claim a pickup and it will appear here with step-by-step buttons.
          </EmptyState>
        ) : (
          <DispatchList items={active} />
        )}
      </section>

      <section className="mt-10">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Nearest open pickups</h2>
          <Link to="/app/pickups" className="text-sm text-sky-700 hover:underline">See all</Link>
        </div>
        {open.isError ? <ErrorState error={open.error} onRetry={() => open.refetch()} /> : nearest.length === 0 ? (
          <p className="text-sm text-ink-400">No food is waiting for a volunteer. Great news, or a quiet evening.</p>
        ) : (
          <DispatchList items={nearest} />
        )}
      </section>
    </>
  );
}

// ------------------------------------------------------------------ recipient

function RecipientHome({ user, impact }: { user: User; impact?: PersonalImpact }) {
  const list = useQuery({ queryKey: ['dispatches', 'mine'], queryFn: () => api.dispatches() });
  const incoming = (list.data ?? []).filter((d) => d.status === 'ASSIGNED' || d.status === 'CLAIMED' || d.status === 'PICKED_UP');
  const toConfirm = (list.data ?? []).filter((d) => d.status === 'DELIVERED' && !d.confirmedAt);

  return (
    <>
      <PageHeader
        eyebrow={roleLabel.RECIPIENT}
        accent={roleColor.RECIPIENT}
        title={`${greeting()}, ${user.name}`}
        description="Food matched to you appears here the moment it is assigned, and you can watch it travel."
      />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Meals received" value={impact?.servingsReceived} icon={<HandHeart className="size-4" />} accent="#e34b6a" />
        <StatCard label="On the way" value={impact?.servingsIncoming} suffix="servings" icon={<Truck className="size-4" />} accent="#e8862a" />
        <StatCard label="Food received" value={impact?.foodReceivedKg} decimals={1} suffix="kg" icon={<Weight className="size-4" />} accent="#1f9d55" />
        <StatCard label="Daily capacity" value={user.dailyCapacity} suffix="meals" icon={<Gauge className="size-4" />} accent="#8b5cf6" />
      </div>

      {toConfirm.length > 0 && (
        <section className="mt-8">
          <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Star className="size-4 text-saffron-600" /> Please confirm these deliveries</h2>
          <DispatchList items={toConfirm} />
        </section>
      )}

      <section className="mt-8">
        <h2 className="mb-4 text-lg font-semibold">Incoming</h2>
        {list.isLoading ? <Skeleton className="h-48" /> : incoming.length === 0 ? (
          <EmptyState icon={<Hourglass className="size-5" />} title="Nothing on the way yet">
            As soon as a kitchen logs surplus that fits your capacity, it is matched to you automatically.
          </EmptyState>
        ) : (
          <DispatchList items={incoming} />
        )}
      </section>
    </>
  );
}

// ------------------------------------------------------------------ coordinator

function AdminHome({ user }: { user: User }) {
  const qc = useQueryClient();
  const impact = useQuery({ queryKey: ['impact'], queryFn: api.impact, refetchInterval: 60_000 });
  const network = useQuery({ queryKey: ['network', 'full'], queryFn: api.network, refetchInterval: 15_000 });
  const verify = useQuery({ queryKey: ['audit-verify'], queryFn: api.verifyAudit });
  const run = useMutation({
    mutationFn: () => api.runMatching(),
    onSuccess: (r) => {
      toast.success(r.dispatches.length ? `Matched ${r.allocatedServings} servings into ${r.dispatches.length} pickups` : 'Nothing to match right now');
      for (const k of ['network', 'dispatches', 'batches']) qc.invalidateQueries({ queryKey: [k] });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const i = impact.data;

  return (
    <>
      <PageHeader
        eyebrow="Command centre"
        accent={roleColor.ADMIN}
        title={`${greeting()}, ${user.name.split(' ')[0]}`}
        description="Everything moving through SevaSync right now: food, matches, volunteers and the health of the audit chain."
        actions={
          <>
            <Button variant="secondary" icon={<BrainCircuit className="size-4" />} loading={run.isPending} onClick={() => run.mutate()}>Run matching</Button>
            <Link to="/app/network"><Button icon={<Globe2 className="size-4" />}>Open live map</Button></Link>
          </>
        }
      />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Meals served" value={i?.servingsDelivered} icon={<HandHeart className="size-4" />} accent="#1f9d55" hint={i && `${num1(i.deliveryRatePercent)}% of settled food delivered`} />
        <StatCard label="Food rescued" value={i?.foodSavedKg} decimals={1} suffix="kg" icon={<Weight className="size-4" />} accent="#e8862a" hint={i && `${num1(i.co2AvoidedKg)} kg CO₂e avoided`} />
        <StatCard label="In transit" value={i?.servingsInTransit} suffix="servings" icon={<Truck className="size-4" />} accent="#0f8fd6" hint={i && `${num(i.servingsAvailable)} waiting for a match`} />
        <StatCard label="Verifications" value={i?.pendingVerifications} icon={<UserCheck className="size-4" />} accent="#8b5cf6" hint={<Link to="/app/partners" className="hover:underline">Review partners →</Link>} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <Card padded={false} className="relative h-[460px] xl:col-span-2">
          {network.data ? (
            <CampusScene network={network.data} className="absolute inset-0" showLabels={false} />
          ) : (
            <Skeleton className="absolute inset-0 rounded-none" />
          )}
          <div className="pointer-events-none absolute left-5 top-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-ink-400">Live network</p>
            <p className="mt-1 text-sm text-ink-300">
              {network.data ? `${network.data.routes.length} active routes · ${network.data.batches.length} open batches` : 'Loading…'}
            </p>
          </div>
          <Link to="/app/network" className="absolute bottom-4 right-4"><Button size="sm" variant="secondary" trailing={<ArrowRight className="size-3.5" />}>Full screen</Button></Link>
        </Card>
        <Card className="flex flex-col">
          <CardHeader title="Live activity" subtitle="Streaming from the server" icon={<Clock3 className="size-4" />} />
          <div className="-mx-2 max-h-[360px] flex-1 overflow-y-auto pr-1"><LiveFeed limit={14} compact /></div>
        </Card>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Last 14 days" subtitle="Servings donated, delivered and wasted" />
          {i ? <TimelineChart data={i.timeline} /> : <Skeleton className="h-64" />}
        </Card>
        <Card spotlight={verify.data?.valid === false ? '#dc2f4f40' : '#1f9d5533'}>
          <CardHeader title="Audit chain" subtitle="HMAC-SHA-256, recomputed end to end" icon={<ShieldCheck className="size-4" />} />
          {verify.data ? (
            <div>
              <p className={verify.data.valid ? 'font-display text-3xl font-semibold text-leaf-700' : 'font-display text-3xl font-semibold text-rose-700'}>
                {verify.data.valid ? 'Intact' : 'Tampered'}
              </p>
              <p className="mt-1 text-sm text-ink-400">{num(verify.data.entries)} entries · {verify.data.reason}</p>
              <Link to="/app/audit"><Button className="mt-5" size="sm" variant="secondary" trailing={<ArrowRight className="size-3.5" />}>Open audit trail</Button></Link>
            </div>
          ) : verify.isError ? <ErrorState error={verify.error} /> : <Skeleton className="h-24" />}
        </Card>
      </div>
    </>
  );
}

// ------------------------------------------------------------------ pending verification

function PendingHome({ user }: { user: User }) {
  return (
    <div className="mx-auto max-w-xl py-16 text-center">
      <div className="mx-auto grid size-16 place-items-center rounded-3xl bg-saffron-400/10 text-saffron-600">
        <Hourglass className="size-7 animate-float" />
      </div>
      <h1 className="mt-6 text-3xl font-semibold">Almost there, {user.name}</h1>
      <p className="mt-3 text-ink-400">
        Organisations are verified by the campus coordinator before they can {user.role === 'DONOR' ? 'log food' : 'receive deliveries'}.
        You'll get access automatically the moment you're approved; this page updates live.
      </p>
      <div className="mt-8 flex justify-center gap-3">
        <Link to="/app/profile"><Button variant="secondary">Complete your profile</Button></Link>
      </div>
    </div>
  );
}
