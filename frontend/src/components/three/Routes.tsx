import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import { Html, QuadraticBezierLine, Trail } from '@react-three/drei';
import * as THREE from 'three';
import { dispatchStatusLabel } from '../../lib/format';
import { dispatchColor, roleColor } from '../../lib/theme';
import type { NetworkRoute } from '../../types';
import type { Selection } from './Nodes';
import { arc } from './arc';

/** A matched hand-over drawn as an arc: dashed while waiting for a volunteer, solid once someone is on it. */
export function RouteArc({ route, from, to, selected, faded, onSelect }: {
  route: NetworkRoute;
  from: [number, number];
  to: [number, number];
  selected: boolean;
  faded?: boolean;
  onSelect: (s: Selection) => void;
}) {
  const ref = useRef<{ material: THREE.Material & { dashOffset?: number } } | null>(null);
  const { start, mid, end } = useMemo(() => arc(from, to), [from, to]);
  const color = faded ? '#34d399' : dispatchColor[route.status];
  const waiting = route.status === 'ASSIGNED';

  useFrame((_, dt) => {
    const m = ref.current?.material;
    if (m && waiting && m.dashOffset !== undefined) m.dashOffset -= dt * 0.8;
  });

  return (
    <QuadraticBezierLine
      // drei's typing does not expose the line ref shape; the material is what we animate
      ref={ref as never}
      start={start}
      end={end}
      mid={mid}
      color={color}
      lineWidth={faded ? 1 : selected ? 3.2 : waiting ? 1.6 : 2.2}
      dashed={waiting}
      dashScale={6}
      dashSize={0.6}
      gapSize={0.4}
      transparent
      opacity={faded ? 0.22 : selected ? 1 : 0.85}
      toneMapped={false}
      onClick={(e) => { e.stopPropagation(); onSelect({ kind: 'route', id: route.id }); }}
    />
  );
}

/**
 * A volunteer in motion: a glowing comet with a light trail. CLAIMED rides from the volunteer's home to the
 * kitchen; PICKED_UP rides the arc from kitchen to recipient. Progress follows the real timestamps and eases
 * forward on screen so state changes never look like teleports.
 */
export function Courier({ route, home, donor, recipient, animate }: {
  route: NetworkRoute;
  home: [number, number] | null;
  donor: [number, number];
  recipient: [number, number];
  animate: boolean;
}) {
  const mesh = useRef<THREE.Mesh>(null);
  const progress = useRef(0);
  const firstSeen = useRef<number | null>(null);
  const status = useRef<string | null>(null);

  const curve = useMemo(() => {
    const pts = route.status === 'PICKED_UP'
      ? arc(donor, recipient)
      : arc(home ?? [donor[0] + 1.2, donor[1] + 1.2], donor, 0.18);
    return new THREE.QuadraticBezierCurve3(new THREE.Vector3(...pts.start), new THREE.Vector3(...pts.mid), new THREE.Vector3(...pts.end));
  }, [route.status, home, donor, recipient]);

  const color = route.status === 'PICKED_UP' ? roleColor.DONOR : roleColor.VOLUNTEER;

  useFrame((_, dt) => {
    if (!mesh.current) return;
    const now = performance.now();
    if (status.current !== route.status || firstSeen.current === null) {
      // a new leg started: restart the on-screen easing from its beginning
      status.current = route.status;
      firstSeen.current = now;
      progress.current = 0;
    }
    const target = targetProgress(route, now - firstSeen.current);
    progress.current = animate ? THREE.MathUtils.damp(progress.current, target, 2.2, dt) : target;
    curve.getPoint(progress.current, mesh.current.position);
  });

  return (
    <group>
      <Trail width={0.9} length={5} color={color} attenuation={(t) => t * t} decay={1.4}>
        <mesh ref={mesh}>
          <sphereGeometry args={[0.085, 16, 16]} />
          <meshBasicMaterial color={color} toneMapped={false} />
        </mesh>
      </Trail>
      {route.status === 'PICKED_UP' && <CourierLabel meshRef={mesh} text={`${route.servings} servings · ${dispatchStatusLabel.PICKED_UP.toLowerCase()}`} />}
    </group>
  );
}

function CourierLabel({ meshRef, text }: { meshRef: React.RefObject<THREE.Mesh | null>; text: string }) {
  const group = useRef<THREE.Group>(null);
  useFrame(() => {
    if (group.current && meshRef.current) {
      group.current.position.copy(meshRef.current.position);
      group.current.position.y += 0.35;
    }
  });
  return (
    <group ref={group}>
      <Html zIndexRange={[5, 0]} style={{ pointerEvents: 'none' }}>
        <div className="scene-label" style={{ fontSize: 10, padding: '2px 7px' }}>{text}</div>
      </Html>
    </group>
  );
}

function targetProgress(route: NetworkRoute, clientMs: number): number {
  const now = Date.now();
  let serverProgress = 0;
  if (route.status === 'CLAIMED' && route.claimedAt) {
    serverProgress = (now - new Date(route.claimedAt).getTime()) / (6 * 60_000);
  } else if (route.status === 'PICKED_UP' && route.pickedUpAt) {
    const minutes = (route.distanceKm / 14) * 60 + 3;
    serverProgress = (now - new Date(route.pickedUpAt).getTime()) / (minutes * 60_000);
  }
  const visible = Math.min(0.9, clientMs / 14_000); // always show some movement after a state change
  return Math.max(0.02, Math.min(0.97, Math.max(serverProgress, visible)));
}
