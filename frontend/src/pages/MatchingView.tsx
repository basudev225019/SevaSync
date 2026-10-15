import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { motion } from 'motion/react';
import { clsx } from 'clsx';
import { ArrowRight, BrainCircuit, CheckCircle2, FlaskConical, Play, Sparkles, Zap } from 'lucide-react';
import { api } from '../services/api';
import { PageHeader } from '../components/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { errorMessage } from '../lib/errors';
import { Badge } from '../components/ui/Badge';
import { StrategyMetricChart, WasteCauseChart } from '../components/charts/Charts';
import GradientText from '../components/reactbits/GradientText';
import { categoryEmoji, num, num1, strategyLabel } from '../lib/format';
import { needColor, roleColor } from '../lib/theme';
import type { BenchmarkReport, MatchResult } from '../types';

export default function MatchingView() {
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: ['matching-settings'], queryFn: api.matchingSettings });
  const [previewStrategy, setPreviewStrategy] = useState<string | null>(null);
  const [preview, setPreview] = useState<MatchResult | null>(null);

  const save = useMutation({
    mutationFn: (v: { strategy?: string; autoMatch?: boolean }) => api.updateMatchingSettings(v.strategy, v.autoMatch),
    onSuccess: (s) => {
      qc.setQueryData(['matching-settings'], s);
      toast.success('Matching settings saved');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const doPreview = useMutation({
    mutationFn: (strategy?: string) => api.previewMatching(strategy),
    onSuccess: setPreview,
    onError: (e) => toast.error(errorMessage(e)),
  });
  const run = useMutation({
    mutationFn: (strategy?: string) => api.runMatching(strategy),
    onSuccess: (r) => {
      setPreview(null);
      toast.success(r.dispatches.length ? `Created ${r.dispatches.length} pickups for ${r.allocatedServings} servings` : 'Nothing to match right now');
      for (const k of ['network', 'dispatches', 'batches']) qc.invalidateQueries({ queryKey: [k] });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const s = settings.data;
  const chosen = previewStrategy ?? s?.activeStrategy;

  return (
    <>
      <PageHeader
        eyebrow="Matching lab"
        accent={roleColor.ADMIN}
        title="Decide who gets what"
        description="Three interchangeable algorithms behind one Java interface (MatchingStrategy). Pick the live one, dry-run any of them, and prove which is best with the simulator."
      />

      {settings.isError && <ErrorState error={settings.error} onRetry={() => settings.refetch()} />}
      {!s ? <Skeleton className="h-48" /> : (
        <div className="grid gap-4 lg:grid-cols-3">
          {s.strategies.map((st) => {
            const active = st.id === s.activeStrategy;
            const isChosen = st.id === chosen;
            return (
              <Card key={st.id} spotlight={active ? '#a78bfa40' : undefined} className={clsx('cursor-pointer transition', isChosen && 'ring-1 ring-violet-400/40')} onClick={() => setPreviewStrategy(st.id)}>
                <div className="flex items-start justify-between gap-2">
                  <span className="grid size-10 place-items-center rounded-xl bg-violet-400/12 text-violet-300">
                    {st.id === 'smart' ? <Sparkles className="size-5" /> : st.id === 'fifo' ? <ArrowRight className="size-5" /> : <Zap className="size-5" />}
                  </span>
                  {active && <Badge color="#34d399" dot>Live</Badge>}
                </div>
                <h3 className="mt-4 font-semibold">{st.name}</h3>
                <p className="mt-1.5 text-sm text-ink-400">{st.description}</p>
                {!active && (
                  <Button size="sm" variant="secondary" className="mt-4" loading={save.isPending && save.variables?.strategy === st.id} onClick={(e) => { e.stopPropagation(); save.mutate({ strategy: st.id }); }}>
                    Make live
                  </Button>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {s && (
        <Card className="mt-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h3 className="font-semibold">Automatic matching</h3>
              <p className="mt-1 text-sm text-ink-400">When on, every new batch or cancelled pickup triggers a matching run within milliseconds.</p>
            </div>
            <button
              onClick={() => save.mutate({ autoMatch: !s.autoMatch })}
              aria-pressed={s.autoMatch}
              className={clsx('relative h-8 w-14 shrink-0 rounded-full transition', s.autoMatch ? 'bg-leaf-500/70' : 'bg-white/10')}
            >
              <motion.span layout className="absolute top-1 size-6 rounded-full bg-white shadow" style={{ left: s.autoMatch ? 28 : 4 }} />
            </button>
          </div>
        </Card>
      )}

      {/* dry run */}
      <Card className="mt-6">
        <CardHeader
          title="Dry run"
          subtitle={`See exactly what “${strategyLabel[chosen ?? ''] ?? chosen ?? '…'}” would do right now, without changing anything`}
          icon={<BrainCircuit className="size-4" />}
          action={
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" loading={doPreview.isPending} onClick={() => doPreview.mutate(chosen)}>Preview</Button>
              <Button size="sm" icon={<Play className="size-3.5" />} loading={run.isPending} onClick={() => run.mutate(chosen)}>Run for real</Button>
            </div>
          }
        />
        {!preview ? (
          <p className="text-sm text-ink-400">Choose a strategy card above and press Preview.</p>
        ) : preview.dispatches.length === 0 ? (
          <EmptyState icon={<CheckCircle2 className="size-5" />} title="Nothing to match">
            {preview.batchesConsidered === 0 ? 'There is no unmatched food right now.' : 'No recipient has room left today.'}
          </EmptyState>
        ) : (
          <>
            <div className="mb-4 flex flex-wrap gap-2 text-xs">
              <Badge color="#34d399">{num(preview.allocatedServings)} servings matched</Badge>
              <Badge color="#fbbf24">{num(preview.unallocatedServings)} still waiting</Badge>
              <Badge color="#38bdf8">avg {num1(preview.averageDistanceKm)} km</Badge>
              <Badge color="#a78bfa">{preview.batchesConsidered} batches · {preview.recipientsAvailable} recipients</Badge>
            </div>
            <ul className="divide-y divide-white/5">
              {preview.dispatches.map((d, i) => (
                <motion.li key={d.id} initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.04 }} className="flex items-center gap-3 py-3 text-sm">
                  <span className="text-lg">{d.batch ? categoryEmoji[d.batch.category] : '🍽️'}</span>
                  <span className="min-w-0 flex-1 truncate text-ink-200">
                    <span className="font-semibold">{d.servings}</span> × {d.batch?.foodName} <span className="text-ink-500">from {d.donor?.name}</span>
                  </span>
                  <ArrowRight className="size-3.5 shrink-0 text-ink-500" />
                  <span className="flex min-w-0 items-center gap-2">
                    <span className="size-2 shrink-0 rounded-full" style={{ background: needColor[d.recipient?.needLevel ?? 'NORMAL'] }} />
                    <span className="truncate text-ink-200">{d.recipient?.name}</span>
                  </span>
                  <span className="w-16 shrink-0 text-right text-xs text-ink-400 tabular">{num1(d.distanceKm)} km</span>
                </motion.li>
              ))}
            </ul>
          </>
        )}
      </Card>

      <Benchmark />
    </>
  );
}

function Benchmark() {
  const [runs, setRuns] = useState(40);
  const [volunteers, setVolunteers] = useState(2);
  const [batches, setBatches] = useState(12);
  const bench = useMutation({
    mutationFn: () => api.benchmark({ runs, volunteers, batches, seed: 2026 }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const r: BenchmarkReport | undefined = bench.data;
  const best = r?.results.find((x) => x.strategy === r.lowestWaste);
  const fifo = r?.results.find((x) => x.strategy === 'fifo');

  return (
    <Card className="mt-6">
      <CardHeader
        title="Strategy benchmark"
        subtitle="Replays seeded synthetic campus days. Every strategy faces exactly the same food, recipients and volunteers."
        icon={<FlaskConical className="size-4" />}
        action={<Button size="sm" icon={<Play className="size-3.5" />} loading={bench.isPending} onClick={() => bench.mutate()}>Run simulation</Button>}
      />
      <div className="grid gap-5 sm:grid-cols-3">
        <Knob label="Simulated days" value={runs} min={5} max={200} step={5} onChange={setRuns} />
        <Knob label="Volunteers on shift" value={volunteers} min={1} max={10} step={1} onChange={setVolunteers} />
        <Knob label="Batches per day" value={batches} min={4} max={40} step={1} onChange={setBatches} />
      </div>

      {bench.isPending && <Skeleton className="mt-6 h-64" />}
      {r && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="mt-8">
          {best && (
            <div className="mb-6 rounded-3xl border border-leaf-400/20 bg-leaf-500/[0.06] p-5">
              <p className="text-xs uppercase tracking-[0.18em] text-leaf-300">Lowest waste over {r.runs} simulated days ({r.elapsedMillis} ms)</p>
              <div className="mt-2 font-display text-3xl font-semibold">
                <GradientText colors={['#34d399', '#fcd581', '#34d399']} animationSpeed={6}>{best.name}</GradientText>
              </div>
              <p className="mt-2 text-sm text-ink-300">
                Wastes {num1(best.wastedPercent)}% of food
                {fifo && best.strategy !== 'fifo' && ` vs ${num1(fifo.wastedPercent)}% for first-come-first-served`}, sends {num1(best.urgentSharePercent)}% of meals to high- or critical-need recipients, average trip {num1(best.averageDistanceKm)} km.
              </p>
            </div>
          )}
          <div className="grid gap-6 lg:grid-cols-2">
            <Metric title="Food wasted" hint="lower is better" rows={r.results} metric="wastedPercent" unit="%" />
            <Metric title="Average trip" hint="lower is better" rows={r.results} metric="averageDistanceKm" unit=" km" />
            <Metric title="Meals to urgent recipients" hint="higher is better" rows={r.results} metric="urgentSharePercent" unit="%" />
            <Metric title="Fairness (Jain's index)" hint="1.0 = perfectly even" rows={r.results} metric="fairnessIndex" />
          </div>
          <div className="mt-8">
            <h4 className="font-semibold">Why food was wasted</h4>
            <p className="mt-1 text-sm text-ink-400">
              Grey waste ("no recipient room") is the same for every strategy, because no algorithm can create capacity that does not exist.
              The strategies differ in the coloured part, which depends on time and volunteers.
            </p>
            <WasteCauseChart rows={r.results} />
          </div>
        </motion.div>
      )}
    </Card>
  );
}

function Metric(props: { title: string; hint: string } & Parameters<typeof StrategyMetricChart>[0]) {
  const { title, hint, ...chart } = props;
  return (
    <div className="rounded-2xl border hairline bg-white/[0.02] p-4">
      <div className="mb-2 flex items-baseline justify-between">
        <h4 className="text-sm font-semibold">{title}</h4>
        <span className="text-[11px] text-ink-500">{hint}</span>
      </div>
      <StrategyMetricChart {...chart} height={150} />
    </div>
  );
}

function Knob({ label, value, min, max, step, onChange }: { label: string; value: number; min: number; max: number; step: number; onChange: (v: number) => void }) {
  const pct = ((value - min) / (max - min)) * 100;
  return (
    <label className="block">
      <span className="flex items-baseline justify-between">
        <span className="label">{label}</span>
        <span className="text-sm font-semibold tabular text-ink-100">{value}</span>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-2 w-full cursor-pointer appearance-none rounded-full accent-violet-400"
        style={{ background: `linear-gradient(90deg, #a78bfa ${pct}%, rgb(255 255 255 / 0.08) ${pct}%)` }}
      />
    </label>
  );
}
