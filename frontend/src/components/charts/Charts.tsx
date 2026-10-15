import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { categoryLabel, shortDate, strategyLabel } from '../../lib/format';
import type { BenchmarkRow, ImpactReport, TimelinePoint } from '../../types';

const AXIS = { stroke: '#a8917b', fontSize: 11, tickLine: false, axisLine: false } as const;
const GRID = { stroke: 'rgb(107 63 29 / 0.1)', vertical: false } as const;
const TOOLTIP = {
  contentStyle: {
    background: 'rgb(255 253 248 / 0.98)',
    border: '1px solid rgb(107 63 29 / 0.14)',
    borderRadius: 14,
    fontSize: 12,
    color: '#43301f',
    boxShadow: '0 18px 40px -20px rgba(120,70,25,.45)',
  },
  cursor: { fill: 'rgb(232 134 42 / 0.06)' },
  labelStyle: { color: '#2a1a10', fontWeight: 600, marginBottom: 4 },
} as const;

/** Food donated vs delivered vs wasted per day. */
export function TimelineChart({ data, height = 260 }: { data: TimelinePoint[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 10, right: 6, left: -18, bottom: 0 }}>
        <defs>
          {[
            ['donated', '#e8862a'],
            ['delivered', '#1f9d55'],
            ['wasted', '#dc2f4f'],
          ].map(([k, c]) => (
            <linearGradient key={k} id={`g-${k}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={c} stopOpacity={0.35} />
              <stop offset="100%" stopColor={c} stopOpacity={0} />
            </linearGradient>
          ))}
        </defs>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="date" tickFormatter={shortDate} {...AXIS} minTickGap={18} />
        <YAxis {...AXIS} />
        <Tooltip {...TOOLTIP} labelFormatter={(l) => shortDate(String(l))} />
        <Area type="monotone" dataKey="donated" name="Donated" stroke="#e8862a" strokeWidth={2} fill="url(#g-donated)" />
        <Area type="monotone" dataKey="delivered" name="Delivered" stroke="#1f9d55" strokeWidth={2} fill="url(#g-delivered)" />
        <Area type="monotone" dataKey="wasted" name="Wasted" stroke="#dc2f4f" strokeWidth={1.5} fill="url(#g-wasted)" />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function CategoryChart({ data, height = 260 }: { data: ImpactReport['categories']; height?: number }) {
  const rows = data.map((d) => ({ ...d, name: categoryLabel[d.category] }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={rows} margin={{ top: 10, right: 6, left: -18, bottom: 0 }} barGap={4}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="name" {...AXIS} interval={0} height={34} tick={<WrappedTick />} />
        <YAxis {...AXIS} />
        <Tooltip {...TOOLTIP} />
        <Bar dataKey="donated" name="Donated" fill="#e8862a" radius={[6, 6, 0, 0]} maxBarSize={26} />
        <Bar dataKey="delivered" name="Delivered" fill="#1f9d55" radius={[6, 6, 0, 0]} maxBarSize={26} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Axis label split over two lines (first word, then the rest), so five categories fit side by side on a phone. */
function WrappedTick({ x = 0, y = 0, payload }: { x?: number; y?: number; payload?: { value: string } }) {
  const [first, ...rest] = String(payload?.value ?? '').split(' ');
  const words = rest.length ? [first, rest.join(' ')] : [first];
  return (
    <text x={x} y={y + 12} textAnchor="middle" fontSize={10} fill="#86705d">
      {words.map((w, i) => <tspan key={i} x={x} dy={i === 0 ? 0 : 11}>{w}</tspan>)}
    </text>
  );
}

const STRATEGY_COLOR: Record<string, string> = { smart: '#1f9d55', 'expiry-first': '#8b5cf6', fifo: '#8c7867' };

/** One metric across strategies (higher or lower is better, stated in the caption). */
export function StrategyMetricChart({ rows, metric, unit, height = 200 }: {
  rows: BenchmarkRow[];
  metric: keyof Pick<BenchmarkRow, 'wastedPercent' | 'deliveredPercent' | 'averageDistanceKm' | 'urgentSharePercent' | 'fairnessIndex' | 'averageDeliveryMinutes'>;
  unit?: string;
  height?: number;
}) {
  const data = rows.map((r) => ({ name: strategyLabel[r.strategy] ?? r.strategy, value: r[metric], fill: STRATEGY_COLOR[r.strategy] ?? '#a08c7a' }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 40, left: 10, bottom: 4 }}>
        <XAxis type="number" hide domain={[0, 'dataMax']} />
        <YAxis type="category" dataKey="name" {...AXIS} width={86} tick={{ fill: '#65503f', fontSize: 12 }} />
        <Tooltip {...TOOLTIP} formatter={(v) => [`${v}${unit ?? ''}`, '']} />
        <Bar dataKey="value" radius={[0, 8, 8, 0]} maxBarSize={22} label={{ position: 'right', fill: '#43301f', fontSize: 12, formatter: (v: unknown) => `${v}${unit ?? ''}` }}>
          {data.map((d) => <Cell key={d.name} fill={d.fill} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Why food was wasted, per strategy, stacked. */
export function WasteCauseChart({ rows, height = 230 }: { rows: BenchmarkRow[]; height?: number }) {
  const data = rows.map((r) => ({
    name: strategyLabel[r.strategy] ?? r.strategy,
    'No recipient room': r.wasteCauses.noRecipientRoom,
    'Waiting for a volunteer': r.wasteCauses.waitingForVolunteer,
    'Unreachable in time': r.wasteCauses.unreachableInTime,
  }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 10, right: 6, left: -12, bottom: 0 }}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="name" {...AXIS} tick={{ fill: '#65503f', fontSize: 12 }} />
        <YAxis {...AXIS} />
        <Tooltip {...TOOLTIP} />
        <Legend wrapperStyle={{ fontSize: 11, color: '#86705d' }} iconType="circle" iconSize={8} />
        <Bar dataKey="No recipient room" stackId="w" fill="#8c7867" maxBarSize={56} />
        <Bar dataKey="Waiting for a volunteer" stackId="w" fill="#ea6c1a" maxBarSize={56} />
        <Bar dataKey="Unreachable in time" stackId="w" fill="#dc2f4f" radius={[8, 8, 0, 0]} maxBarSize={56} />
      </BarChart>
    </ResponsiveContainer>
  );
}
