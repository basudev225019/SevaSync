import type { LatLng } from '../types';

/** One scene unit = half a kilometre. A 4 km radius campus fits comfortably in view. */
export const KM_PER_UNIT = 0.5;
const KM_PER_DEG = 111.32;

/** Flat local projection around {@code center}: x grows east, z grows south (three.js convention). */
export function project(p: LatLng, center: LatLng): [number, number] {
  const x = ((p.lng - center.lng) * KM_PER_DEG * Math.cos((center.lat * Math.PI) / 180)) / KM_PER_UNIT;
  const z = (-(p.lat - center.lat) * KM_PER_DEG) / KM_PER_UNIT;
  return [x, z];
}

export function unproject(x: number, z: number, center: LatLng): LatLng {
  const lat = center.lat + (-z * KM_PER_UNIT) / KM_PER_DEG;
  const lng = center.lng + (x * KM_PER_UNIT) / (KM_PER_DEG * Math.cos((center.lat * Math.PI) / 180));
  return { lat, lng };
}

export function distanceKm(a: LatLng, b: LatLng): number {
  const r = 6371.0088;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
}

export const CAMPUS: LatLng = { lat: 12.9716, lng: 77.5946 };
