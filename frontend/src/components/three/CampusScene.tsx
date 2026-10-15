import { Suspense, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { OrbitControls, Sparkles } from '@react-three/drei';
import { Bloom, EffectComposer, Vignette } from '@react-three/postprocessing';
import * as THREE from 'three';
import type { OrbitControls as OrbitControlsImpl } from 'three-stdlib';
import { project } from '../../lib/geo';
import type { Network, NetworkBatch } from '../../types';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { Ground } from './Ground';
import { DonorTower, RecipientHub, VolunteerMarker, type Selection } from './Nodes';
import { Courier, RouteArc } from './Routes';
import '../../App.css';

export type { Selection };

interface CampusSceneProps {
  network: Network;
  /** "hero": cinematic intro + slow auto-rotation for the landing page. "app": interactive command view. */
  mode?: 'hero' | 'app';
  selected?: Selection;
  onSelect?: (s: Selection) => void;
  /** Focus one participant: everything unrelated is dimmed. */
  focusId?: string | null;
  showLabels?: boolean;
  className?: string;
}

/** The live campus redistribution network in 3D. Purely a view of {@link Network}; it never fetches. */
export function CampusScene({ network, mode = 'app', selected = null, onSelect, focusId, showLabels = true, className }: CampusSceneProps) {
  const reduced = useReducedMotion();
  const animate = !reduced;
  const select = onSelect ?? (() => undefined);

  const layout = useMemo(() => {
    const c = network.center;
    const pos = new Map<string, [number, number]>();
    for (const d of network.donors) if (d.location) pos.set(d.id, project(d.location, c));
    for (const r of network.recipients) if (r.location) pos.set(r.id, project(r.location, c));
    for (const v of network.volunteers) if (v.location) pos.set(v.id, project(v.location, c));
    const byDonor = new Map<string, NetworkBatch[]>();
    for (const b of network.batches) {
      if (b.remainingServings <= 0) continue;
      byDonor.set(b.donorId, [...(byDonor.get(b.donorId) ?? []), b]);
    }
    const related = new Set<string>();
    if (focusId) {
      related.add(focusId);
      for (const r of network.routes) {
        if ([r.donorId, r.recipientId, r.volunteerId].includes(focusId)) {
          related.add(r.donorId);
          related.add(r.recipientId);
          if (r.volunteerId) related.add(r.volunteerId);
        }
      }
    }
    return { pos, byDonor, related };
  }, [network, focusId]);

  const dim = (id: string) => !!focusId && !layout.related.has(id);
  const busyVolunteers = new Set(network.routes.filter((r) => r.volunteerId && r.status !== 'ASSIGNED').map((r) => r.volunteerId));

  return (
    <div className={className} style={{ touchAction: 'none' }}>
      <Canvas
        dpr={[1, 1.75]}
        camera={{ position: mode === 'hero' ? [0, 24, 34] : [0, 11, 15], fov: 42, near: 0.1, far: 120 }}
        gl={{ antialias: true, powerPreference: 'high-performance', alpha: false }}
        onPointerMissed={() => select(null)}
        fallback={<div className="grid h-full place-items-center text-sm text-ink-400">3D view needs WebGL, which this browser has turned off.</div>}
      >
        <color attach="background" args={['#05070c']} />
        <fog attach="fog" args={['#05070c', 20, 46]} />
        <ambientLight intensity={0.35} />
        <directionalLight position={[8, 14, 6]} intensity={0.7} color="#ffe8c2" />
        <pointLight position={[0, 3, 0]} intensity={6} distance={10} color="#fbbf24" />

        <Suspense fallback={null}>
          <Ground animate={animate} />

          {network.donors.map((d) => {
            const p = layout.pos.get(d.id);
            return p ? (
              <DonorTower
                key={d.id}
                donor={d}
                batches={layout.byDonor.get(d.id) ?? []}
                position={p}
                selected={selected?.id === d.id}
                dimmed={dim(d.id)}
                showLabel={showLabels}
                animate={animate}
                onSelect={select}
              />
            ) : null;
          })}

          {network.recipients.map((r) => {
            const p = layout.pos.get(r.id);
            return p ? (
              <RecipientHub
                key={r.id}
                recipient={r}
                position={p}
                selected={selected?.id === r.id}
                dimmed={dim(r.id)}
                showLabel={showLabels}
                animate={animate}
                onSelect={select}
              />
            ) : null;
          })}

          {network.volunteers.filter((v) => !busyVolunteers.has(v.id)).map((v) => {
            const p = layout.pos.get(v.id);
            return p ? (
              <VolunteerMarker key={v.id} volunteer={v} position={p} selected={selected?.id === v.id} animate={animate} onSelect={select} />
            ) : null;
          })}

          {network.recentDeliveries.map((r) => {
            const a = layout.pos.get(r.donorId);
            const b = layout.pos.get(r.recipientId);
            return a && b ? <RouteArc key={`done-${r.id}`} route={r} from={a} to={b} faded selected={false} onSelect={select} /> : null;
          })}

          {network.routes.map((r) => {
            const a = layout.pos.get(r.donorId);
            const b = layout.pos.get(r.recipientId);
            if (!a || !b) return null;
            const faded = !!focusId && !layout.related.has(r.donorId);
            return (
              <group key={r.id}>
                <RouteArc route={r} from={a} to={b} selected={selected?.id === r.id} faded={faded} onSelect={select} />
                {(r.status === 'CLAIMED' || r.status === 'PICKED_UP') && (
                  <Courier
                    route={r}
                    home={r.volunteerId ? layout.pos.get(r.volunteerId) ?? null : null}
                    donor={a}
                    recipient={b}
                    animate={animate}
                  />
                )}
              </group>
            );
          })}

          {animate && <Sparkles count={70} scale={[26, 5, 26]} position={[0, 2.5, 0]} size={2.2} speed={0.25} color="#fcd581" opacity={0.35} />}
        </Suspense>

        <Rig mode={mode} animate={animate} />
        <EffectComposer multisampling={0}>
          <Bloom mipmapBlur intensity={0.9} luminanceThreshold={0.42} luminanceSmoothing={0.2} radius={0.6} />
          <Vignette eskil={false} offset={0.25} darkness={0.75} />
        </EffectComposer>
      </Canvas>
    </div>
  );
}

/** Camera: cinematic fly-in on the landing page, damped orbit controls everywhere. */
function Rig({ mode, animate }: { mode: 'hero' | 'app'; animate: boolean }) {
  const controls = useRef<OrbitControlsImpl>(null);
  const { camera, size } = useThree();
  const intro = useRef(mode === 'hero' && animate ? 0 : 1);
  const from = useMemo(() => new THREE.Vector3(0, 24, 34), []);
  const to = useMemo(() => (mode === 'hero' ? new THREE.Vector3(2.5, 8.5, 14.5) : new THREE.Vector3(0, 11, 15)), [mode]);

  // in the hero the network sits to the right of the headline on wide screens: shift the image, not the orbit
  useEffect(() => {
    const cam = camera as THREE.PerspectiveCamera;
    if (mode === 'hero' && size.width > 900) {
      cam.setViewOffset(size.width, size.height, -size.width * 0.2, 0, size.width, size.height);
    } else {
      cam.clearViewOffset();
    }
  }, [camera, mode, size.width, size.height]);

  useFrame((_, dt) => {
    if (intro.current < 1) {
      intro.current = Math.min(1, intro.current + dt / 3.2);
      const t = 1 - Math.pow(1 - intro.current, 3);
      camera.position.lerpVectors(from, to, t);
      controls.current?.update();
    }
  });

  return (
    <OrbitControls
      ref={controls}
      makeDefault
      enablePan={mode === 'app'}
      enableZoom={mode === 'app'}
      enableDamping
      dampingFactor={0.08}
      autoRotate={mode === 'hero' && animate}
      autoRotateSpeed={0.35}
      minDistance={4}
      maxDistance={34}
      maxPolarAngle={Math.PI / 2.15}
      target={[0, 0.5, 0]}
    />
  );
}
