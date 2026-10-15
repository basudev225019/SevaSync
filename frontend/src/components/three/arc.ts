export type V3 = [number, number, number];

/** Bezier from a to b, lifted in the middle in proportion to its length. */
export function arc(a: [number, number], b: [number, number], lift = 0.32): { start: V3; mid: V3; end: V3 } {
  const start: V3 = [a[0], 0.25, a[1]];
  const end: V3 = [b[0], 0.25, b[1]];
  const len = Math.hypot(b[0] - a[0], b[1] - a[1]);
  const mid: V3 = [(a[0] + b[0]) / 2, 0.4 + len * lift, (a[1] + b[1]) / 2];
  return { start, mid, end };
}
