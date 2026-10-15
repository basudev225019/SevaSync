import { useMemo, useRef, type PointerEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { MapPin } from 'lucide-react';
import { api } from '../services/api';
import { CAMPUS, distanceKm, project, unproject } from '../lib/geo';
import { roleColor } from '../lib/theme';
import type { LatLng } from '../types';

const RANGE = 12; // scene units shown from the centre in each direction (6 km)

/**
 * Offline campus map: click or drag to place a pin. Uses the same projection as the 3D scene, so the spot picked
 * here is exactly where the participant appears in the live network. Needs no map tiles or third-party scripts.
 */
export function MapPicker({ value, onChange, color = '#e8862a' }: {
  value: LatLng | null;
  onChange: (p: LatLng) => void;
  color?: string;
}) {
  const svg = useRef<SVGSVGElement>(null);
  const network = useQuery({ queryKey: ['network', 'public'], queryFn: api.publicNetwork, staleTime: 60_000 });
  const center = network.data?.center ?? CAMPUS;

  const others = useMemo(() => {
    const n = network.data;
    if (!n) return [];
    return [
      ...n.donors.map((d) => ({ id: d.id, p: d.location, c: roleColor.DONOR })),
      ...n.recipients.map((r) => ({ id: r.id, p: r.location, c: roleColor.RECIPIENT })),
    ].filter((o) => o.p).map((o) => ({ ...o, xy: project(o.p!, center) }));
  }, [network.data, center]);

  const place = (e: PointerEvent<SVGSVGElement>) => {
    const el = svg.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * RANGE * 2 - RANGE;
    const z = ((e.clientY - rect.top) / rect.height) * RANGE * 2 - RANGE;
    onChange(unproject(x, z, center));
  };

  const pin = value ? project(value, center) : null;
  const km = value ? distanceKm(value, center) : null;

  return (
    <div>
      <div className="relative overflow-hidden rounded-2xl border hairline bg-night-950">
        <svg
          ref={svg}
          viewBox={`${-RANGE} ${-RANGE} ${RANGE * 2} ${RANGE * 2}`}
          className="aspect-square w-full cursor-crosshair touch-none select-none"
          onPointerDown={(e) => {
            (e.target as Element).setPointerCapture?.(e.pointerId);
            place(e);
          }}
          onPointerMove={(e) => e.buttons === 1 && place(e)}
          role="application"
          aria-label="Campus map: click to set your location"
        >
          <defs>
            <pattern id="mp-grid" width="1" height="1" patternUnits="userSpaceOnUse">
              <path d="M 1 0 L 0 0 0 1" fill="none" stroke="#ead9c3" strokeWidth="0.04" />
            </pattern>
            <radialGradient id="mp-glow">
              <stop offset="0" stopColor="#e8862a" stopOpacity="0.16" />
              <stop offset="1" stopColor="#e8862a" stopOpacity="0" />
            </radialGradient>
          </defs>
          <rect x={-RANGE} y={-RANGE} width={RANGE * 2} height={RANGE * 2} fill="url(#mp-grid)" />
          <circle r="4" fill="url(#mp-glow)" />
          {[2, 4, 6, 8, 10].map((r) => (
            <circle key={r} r={r} fill="none" stroke="#dcc6a8" strokeWidth="0.05" strokeDasharray="0.3 0.25" />
          ))}
          {[1, 2, 3, 4, 5].map((k) => (
            <text key={k} x={k * 2 + 0.15} y={-0.2} fontSize="0.45" fill="#a8917b">{k} km</text>
          ))}
          <polygon
            points={Array.from({ length: 6 }, (_, i) => {
              const a = Math.PI / 6 + (i / 6) * Math.PI * 2;
              return `${Math.cos(a) * 1.7},${Math.sin(a) * 1.7}`;
            }).join(' ')}
            fill="#e8862a10"
            stroke="#e8862a"
            strokeOpacity="0.5"
            strokeWidth="0.06"
          />
          <text x={0} y={0.18} fontSize="0.5" textAnchor="middle" fill="#f2a950" opacity="0.8">CAMPUS</text>
          {others.map((o) => (
            <circle key={o.id} cx={o.xy[0]} cy={o.xy[1]} r="0.22" fill={o.c} opacity="0.55" />
          ))}
          {pin && (
            <g transform={`translate(${pin[0]} ${pin[1]})`}>
              <motion.circle r={0.5} fill={color} initial={{ opacity: 0.6, scale: 0.5 }} animate={{ opacity: 0, scale: 2.2 }} transition={{ duration: 1.6, repeat: Infinity }} />
              {/* ring + white outline + label so the chosen spot stands out from the other kitchens' dots */}
              <circle r="0.62" fill="none" stroke={color} strokeWidth="0.1" />
              <circle r="0.36" fill={color} stroke="#2a1a10" strokeWidth="0.1" />
              <text y={-0.95} fontSize="0.55" fontWeight="600" textAnchor="middle" fill="#2a1a10" stroke="#ffffff" strokeWidth="0.12" paintOrder="stroke">you</text>
            </g>
          )}
        </svg>
        <div className="pointer-events-none absolute bottom-2 left-2 flex gap-2 text-[10px] text-ink-400">
          <span className="rounded-full bg-night-900/80 px-2 py-0.5"><span style={{ color: roleColor.DONOR }}>●</span> kitchens</span>
          <span className="rounded-full bg-night-900/80 px-2 py-0.5"><span style={{ color: roleColor.RECIPIENT }}>●</span> shelters</span>
        </div>
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-xs text-ink-400">
        <MapPin className="size-3.5" />
        {value ? `${value.lat.toFixed(5)}, ${value.lng.toFixed(5)} · ${km!.toFixed(1)} km from campus centre` : 'Tap the map where you are based (you can change it later).'}
      </p>
    </div>
  );
}
