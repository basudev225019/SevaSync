import type { BatchStatus, DispatchStatus, NeedLevel, Role } from '../types';

/** One colour per role, shared by the UI and the 3D scene. Mirrors the --color-role-* tokens in index.css. */
export const roleColor: Record<Role, string> = {
  DONOR: '#e8862a',
  RECIPIENT: '#e34b6a',
  VOLUNTEER: '#0f8fd6',
  ADMIN: '#8b5cf6',
};

export const needColor: Record<NeedLevel, string> = {
  NORMAL: '#a08c7a',
  HIGH: '#ea6c1a',
  CRITICAL: '#dc2f4f',
};

export const dispatchColor: Record<DispatchStatus, string> = {
  ASSIGNED: '#8b5cf6',
  CLAIMED: '#0f8fd6',
  PICKED_UP: '#e8862a',
  DELIVERED: '#1f9d55',
  CANCELLED: '#8c7867',
};

export const batchColor: Record<BatchStatus, string> = {
  AVAILABLE: '#e8862a',
  ALLOCATED: '#0f8fd6',
  COMPLETED: '#1f9d55',
  EXPIRED: '#dc2f4f',
  WITHDRAWN: '#8c7867',
};

/** Freshness 1 → 0 mapped green → amber → red. */
export function freshnessColor(f: number): string {
  const clamped = Math.max(0, Math.min(1, f));
  if (clamped > 0.5) return mix('#e8862a', '#1f9d55', (clamped - 0.5) * 2);
  return mix('#dc2f4f', '#e8862a', clamped * 2);
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
