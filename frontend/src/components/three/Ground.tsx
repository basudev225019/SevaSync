import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import { Grid, Line } from '@react-three/drei';
import * as THREE from 'three';
import { KM_PER_UNIT } from '../../lib/geo';

/** Dark ground with a fading grid, distance rings every kilometre and a glowing campus boundary. */
export function Ground({ animate = true }: { animate?: boolean }) {
  const rings = useMemo(() => [1, 2, 3, 4, 5].map((km) => circle(km / KM_PER_UNIT, 128)), []);
  const campus = useMemo(() => circle(1.7, 6, Math.PI / 6), []);
  const pulse = useRef<THREE.Mesh>(null);

  useFrame(({ clock }) => {
    if (!pulse.current || !animate) return;
    const t = (clock.elapsedTime % 5) / 5;
    pulse.current.scale.setScalar(1.7 + t * 6);
    (pulse.current.material as THREE.MeshBasicMaterial).opacity = 0.1 * (1 - t) * (1 - t);
  });

  return (
    <group>
      <mesh rotation-x={-Math.PI / 2} position-y={-0.02} receiveShadow>
        <circleGeometry args={[40, 64]} />
        <meshStandardMaterial color="#05070c" roughness={1} metalness={0} />
      </mesh>
      <Grid
        position={[0, -0.01, 0]}
        args={[60, 60]}
        cellSize={0.5}
        cellThickness={0.5}
        cellColor="#141b29"
        sectionSize={2}
        sectionThickness={0.9}
        sectionColor="#1d2738"
        fadeDistance={34}
        fadeStrength={1.6}
        infiniteGrid
      />
      {rings.map((pts, i) => (
        <Line key={i} points={pts} color="#2a3550" lineWidth={1} transparent opacity={0.55 - i * 0.07} dashed dashSize={0.25} gapSize={0.2} />
      ))}
      {/* campus boundary */}
      <Line points={campus} color="#fbbf24" lineWidth={1.6} transparent opacity={0.55} />
      <mesh rotation-x={-Math.PI / 2} position-y={0.005}>
        <circleGeometry args={[1.7, 6, Math.PI / 6]} />
        <meshBasicMaterial color="#fbbf24" transparent opacity={0.05} />
      </mesh>
      <mesh ref={pulse} rotation-x={-Math.PI / 2} position-y={0.01}>
        <ringGeometry args={[0.985, 1, 96]} />
        <meshBasicMaterial color="#fbbf24" transparent opacity={0.15} toneMapped={false} />
      </mesh>
    </group>
  );
}

function circle(radius: number, segments: number, offset = 0): [number, number, number][] {
  const pts: [number, number, number][] = [];
  for (let i = 0; i <= segments; i++) {
    const a = offset + (i / segments) * Math.PI * 2;
    pts.push([Math.cos(a) * radius, 0.01, Math.sin(a) * radius]);
  }
  return pts;
}
