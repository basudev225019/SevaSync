import type { BatchStatus, DispatchStatus, NeedLevel, Role } from '../types';

/** One colour per role, shared by the UI and the 3D scene. Mirrors the --color-role-* tokens in index.css. */
export const roleColor: Record<Role, string> = {
  DONOR: '#fbbf24',
  RECIPIENT: '#fb7185',
  VOLUNTEER: '#38bdf8',
  ADMIN: '#a78bfa',
};

export const needColor: Record<NeedLevel, string> = {
  NORMAL: '#94a3b8',
  HIGH: '#fb923c',
  CRITICAL: '#f43f5e',
};

export const dispatchColor: Record<DispatchStatus, string> = {
  ASSIGNED: '#a78bfa',
  CLAIMED: '#38bdf8',
  PICKED_UP: '#fbbf24',
  DELIVERED: '#34d399',
  CANCELLED: '#64748b',
};

export const batchColor: Record<BatchStatus, string> = {
  AVAILABLE: '#fbbf24',
  ALLOCATED: '#38bdf8',
  COMPLETED: '#34d399',
  EXPIRED: '#f43f5e',
  WITHDRAWN: '#64748b',
};

/** Freshness 1 → 0 mapped green → amber → red. */
export function freshnessColor(f: number): string {
  const clamped = Math.max(0, Math.min(1, f));
  if (clamped > 0.5) return mix('#fbbf24', '#34d399', (clamped - 0.5) * 2);
  return mix('#f43f5e', '#fbbf24', clamped * 2);
}

function mix(a: string, b: string, t: number): string {
  const pa = hex(a);
  const pb = hex(b);
  const c = pa.map((v, i) => Math.round(v + (pb[i] - v) * t));
  return `#${c.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}

function hex(h: string): number[] {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
