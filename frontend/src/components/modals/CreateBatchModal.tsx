import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { motion } from 'motion/react';
import { Leaf, ShieldAlert, ShieldCheck } from 'lucide-react';
import { clsx } from 'clsx';
import { api } from '../../services/api';
import { categoryEmoji, categoryLabel, clock, minutesLeft } from '../../lib/format';
import { freshnessColor } from '../../lib/theme';
import type { Batch, FoodCategory } from '../../types';
import { Button } from '../ui/Button';
import { Input, TextArea } from '../ui/Field';
import { Modal } from '../ui/Modal';
import { errorMessage } from '../../lib/errors';
import { useNow } from '../../hooks/useNow';

const MAX_SHELF: Record<FoodCategory, number> = {
  COOKED_MEAL: 240,
  BEVERAGE: 480,
  BAKERY: 720,
  FRUITS_VEGETABLES: 1440,
  PACKAGED: 2880,
};
const MIN_REMAINING = 30;

/**
 * Surplus intake form. Mirrors the server's SafetyValidator rules live, so the donor sees "safe until 21:40" or
 * exactly why it would be refused before submitting. (The server re-checks everything regardless.)
 */
export function BatchForm({ onDone, compact }: { onDone?: (b: Batch) => void; compact?: boolean }) {
  const qc = useQueryClient();
  const [foodName, setFoodName] = useState('');
  const [category, setCategory] = useState<FoodCategory>('COOKED_MEAL');
  const [vegetarian, setVegetarian] = useState(true);
  const [servings, setServings] = useState(40);
  const [cookedAgo, setCookedAgo] = useState(20);
  const [shelf, setShelf] = useState(180);
  const [notes, setNotes] = useState('');

  const now = useNow(30_000);
  const max = MAX_SHELF[category];
  const shelfClamped = Math.min(shelf, max);
  const remaining = shelfClamped - cookedAgo;
  const verdict = useMemo(() => {
    if (shelf > max) return { ok: false, text: `${categoryLabel[category]} may be kept at most ${max / 60} h.` };
    if (remaining <= 0) return { ok: false, text: 'This food has already expired. It cannot be redistributed.' };
    if (remaining < MIN_REMAINING) return { ok: false, text: `Only ${remaining} min left. SevaSync needs at least ${MIN_REMAINING} min to get it to someone.` };
    return { ok: true, text: `Safe until ${clock(new Date(now + remaining * 60_000).toISOString())} · ${minutesLeft(remaining)} to deliver it.` };
  }, [shelf, max, remaining, category, now]);

  const log = useMutation({
    mutationFn: () =>
      api.logBatch({
        foodName: foodName.trim(),
        category,
        vegetarian,
        servings,
        shelfLifeMinutes: shelfClamped,
        preparedAt: new Date(Date.now() - cookedAgo * 60_000).toISOString(),
        notes: notes.trim(),
      }),
    onSuccess: (b) => {
      toast.success(`${b.servings} servings of ${b.foodName} logged. Matching now…`);
      for (const k of ['batches', 'network', 'dispatches', 'me']) qc.invalidateQueries({ queryKey: [k] });
      setFoodName('');
      setNotes('');
      onDone?.(b);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (verdict.ok && foodName.trim()) log.mutate();
  };

  const ratio = Math.max(0, remaining) / shelfClamped;

  return (
    <form onSubmit={submit} className="space-y-5">
      <Input label="What is it?" value={foodName} onChange={(e) => setFoodName(e.target.value)} placeholder="Vegetable biryani" maxLength={80} required />

      <div>
        <span className="label">Category</span>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          {(Object.keys(MAX_SHELF) as FoodCategory[]).map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => {
                setCategory(c);
                setShelf(Math.round(MAX_SHELF[c] * 0.75));
              }}
              aria-pressed={c === category}
              className={clsx('rounded-2xl border px-2 py-3 text-center transition', c === category ? 'border-saffron-400/50 bg-saffron-400/10' : 'border-white/8 hover:bg-white/[0.03]')}
            >
              <span className="block text-xl">{categoryEmoji[c]}</span>
              <span className="mt-1 block text-[11px] font-semibold text-ink-200">{categoryLabel[c]}</span>
              <span className="block text-[10px] text-ink-500">max {MAX_SHELF[c] / 60} h</span>
            </button>
          ))}
        </div>
      </div>

      <div className={clsx('grid gap-5', !compact && 'sm:grid-cols-2')}>
        <Slider label="Servings" value={servings} min={1} max={500} step={1} onChange={setServings} suffix="servings" />
        <Slider label="Cooked" value={cookedAgo} min={0} max={Math.max(0, max - 10)} step={5} onChange={setCookedAgo} format={(v) => (v === 0 ? 'just now' : `${minutesLeft(v)} ago`)} />
        <Slider label="Safe to eat for" value={shelfClamped} min={30} max={max} step={15} onChange={setShelf} format={(v) => `${minutesLeft(v)} after cooking`} />
        <div>
          <span className="label">Diet</span>
          <button
            type="button"
            onClick={() => setVegetarian((v) => !v)}
            className={clsx('flex w-full items-center justify-between rounded-xl border px-3.5 py-2.5 text-sm transition', vegetarian ? 'border-leaf-400/40 bg-leaf-500/10 text-leaf-300' : 'border-white/8 text-ink-300')}
            aria-pressed={vegetarian}
          >
            <span className="flex items-center gap-2"><Leaf className="size-4" /> {vegetarian ? 'Vegetarian' : 'Non-vegetarian'}</span>
            <span className={clsx('relative h-5 w-9 rounded-full transition', vegetarian ? 'bg-leaf-500/60' : 'bg-white/10')}>
              <motion.span layout className="absolute top-0.5 size-4 rounded-full bg-white" style={{ left: vegetarian ? 18 : 2 }} />
            </span>
          </button>
        </div>
      </div>

      <TextArea label="Notes for the volunteer (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Collect from the back door, bring a bag" maxLength={240} />

      <motion.div
        layout
        className={clsx('flex items-start gap-3 rounded-2xl border px-4 py-3 text-sm', verdict.ok ? 'border-leaf-400/25 bg-leaf-500/[0.07] text-leaf-200' : 'border-rose-400/25 bg-rose-500/[0.07] text-rose-200')}
      >
        {verdict.ok ? <ShieldCheck className="mt-0.5 size-4 shrink-0" /> : <ShieldAlert className="mt-0.5 size-4 shrink-0" />}
        <div className="flex-1">
          {verdict.text}
          {verdict.ok && (
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-white/8">
              <div className="h-full rounded-full" style={{ width: `${ratio * 100}%`, background: freshnessColor(ratio) }} />
            </div>
          )}
        </div>
      </motion.div>

      <Button type="submit" size="lg" className="w-full" loading={log.isPending} disabled={!verdict.ok || !foodName.trim()}>
        Log {servings} servings
      </Button>
    </form>
  );
}

function Slider({ label, value, min, max, step, onChange, suffix, format }: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  suffix?: string;
  format?: (v: number) => string;
}) {
  const pct = ((value - min) / Math.max(1, max - min)) * 100;
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <span className="label">{label}</span>
        <span className="text-sm font-semibold text-ink-100 tabular">{format ? format(value) : `${value} ${suffix ?? ''}`}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        aria-label={label}
        className="h-2 w-full cursor-pointer appearance-none rounded-full bg-white/8 accent-saffron-400"
        style={{ background: `linear-gradient(90deg, #fbbf24 ${pct}%, rgb(255 255 255 / 0.08) ${pct}%)` }}
      />
    </div>
  );
}

export function CreateBatchModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="Log surplus food" subtitle="It will be safety-checked and matched automatically." width="lg">
      <BatchForm onDone={onClose} />
    </Modal>
  );
}
