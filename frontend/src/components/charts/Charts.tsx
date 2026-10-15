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

const AXIS = { stroke: '#4b5568', fontSize: 11, tickLine: false, axisLine: false } as const;
const GRID = { stroke: 'rgb(255 255 255 / 0.05)', vertical: false } as const;
const TOOLTIP = {
  contentStyle: {
    background: 'rgb(10 13 20 / 0.95)',
    border: '1px solid rgb(255 255 255 / 0.1)',
    borderRadius: 14,
    fontSize: 12,
    color: '#dfe4ee',
    boxShadow: '0 18px 40px -18px rgba(0,0,0,.8)',
  },
  cursor: { fill: 'rgb(255 255 255 / 0.03)' },
  labelStyle: { color: '#f4f6fb', fontWeight: 600, marginBottom: 4 },
} as const;

/** Food donated vs delivered vs wasted per day. */
export function TimelineChart({ data, height = 260 }: { data: TimelinePoint[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 10, right: 6, left: -18, bottom: 0 }}>
        <defs>
          {[
            ['donated', '#fbbf24'],
            ['delivered', '#34d399'],
            ['wasted', '#f43f5e'],
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
        <Area type="monotone" dataKey="donated" name="Donated" stroke="#fbbf24" strokeWidth={2} fill="url(#g-donated)" />
        <Area type="monotone" dataKey="delivered" name="Delivered" stroke="#34d399" strokeWidth={2} fill="url(#g-delivered)" />
        <Area type="monotone" dataKey="wasted" name="Wasted" stroke="#f43f5e" strokeWidth={1.5} fill="url(#g-wasted)" />
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
        <XAxis dataKey="name" {...AXIS} interval={0} tick={{ fontSize: 10, fill: '#8a93a6' }} />
        <YAxis {...AXIS} />
        <Tooltip {...TOOLTIP} />
        <Bar dataKey="donated" name="Donated" fill="#fbbf24" radius={[6, 6, 0, 0]} maxBarSize={26} />
        <Bar dataKey="delivered" name="Delivered" fill="#34d399" radius={[6, 6, 0, 0]} maxBarSize={26} />
      </BarChart>
    </ResponsiveContainer>
  );
}

const STRATEGY_COLOR: Record<string, string> = { smart: '#34d399', 'expiry-first': '#a78bfa', fifo: '#64748b' };

/** One metric across strategies (higher or lower is better, stated in the caption). */
export function StrategyMetricChart({ rows, metric, unit, height = 200 }: {
  rows: BenchmarkRow[];
  metric: keyof Pick<BenchmarkRow, 'wastedPercent' | 'deliveredPercent' | 'averageDistanceKm' | 'urgentSharePercent' | 'fairnessIndex' | 'averageDeliveryMinutes'>;
  unit?: string;
  height?: number;
}) {
  const data = rows.map((r) => ({ name: strategyLabel[r.strategy] ?? r.strategy, value: r[metric], fill: STRATEGY_COLOR[r.strategy] ?? '#94a3b8' }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 40, left: 10, bottom: 4 }}>
        <XAxis type="number" hide domain={[0, 'dataMax']} />
        <YAxis type="category" dataKey="name" {...AXIS} width={86} tick={{ fill: '#b7bfce', fontSize: 12 }} />
        <Tooltip {...TOOLTIP} formatter={(v) => [`${v}${unit ?? ''}`, '']} />
        <Bar dataKey="value" radius={[0, 8, 8, 0]} maxBarSize={22} label={{ position: 'right', fill: '#dfe4ee', fontSize: 12, formatter: (v: unknown) => `${v}${unit ?? ''}` }}>
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
        <XAxis dataKey="name" {...AXIS} tick={{ fill: '#b7bfce', fontSize: 12 }} />
        <YAxis {...AXIS} />
        <Tooltip {...TOOLTIP} />
        <Legend wrapperStyle={{ fontSize: 11, color: '#8a93a6' }} iconType="circle" iconSize={8} />
        <Bar dataKey="No recipient room" stackId="w" fill="#64748b" maxBarSize={56} />
        <Bar dataKey="Waiting for a volunteer" stackId="w" fill="#fb923c" maxBarSize={56} />
        <Bar dataKey="Unreachable in time" stackId="w" fill="#f43f5e" radius={[8, 8, 0, 0]} maxBarSize={56} />
      </BarChart>
    </ResponsiveContainer>
  );
}
