import { useMemo, useRef, useState } from 'react';
import { useFrame, type ThreeEvent } from '@react-three/fiber';
import * as THREE from 'three';
import { donorTypeLabel, needLabel, recipientTypeLabel, vehicleLabel } from '../../lib/format';
import { freshnessColor, needColor, roleColor } from '../../lib/theme';
import type { NetworkBatch, NetworkDonor, NetworkRecipient, NetworkVolunteer } from '../../types';
import { useSceneLabel, type LabelStore } from './labelStore';

export type Selection = { kind: 'donor' | 'recipient' | 'volunteer' | 'route'; id: string } | null;

interface NodeProps {
  position: [number, number];
  selected: boolean;
  dimmed: boolean;
  showLabel: boolean;
  animate: boolean;
  onSelect: (s: Selection) => void;
  labels: LabelStore;
}

/** Stable anchor function for a label at a fixed world position. */
function useAnchor(x: number, y: number, z: number) {
  return useMemo(() => {
    const v = new THREE.Vector3(x, y, z);
    return () => v;
  }, [x, y, z]);
}

function useHover() {
  const [hovered, setHovered] = useState(false);
  const bind = {
    onPointerOver: (e: ThreeEvent<PointerEvent>) => {
      e.stopPropagation();
      setHovered(true);
      document.body.style.cursor = 'pointer';
    },
    onPointerOut: () => {
      setHovered(false);
      document.body.style.cursor = '';
    },
  };
  return [hovered, bind] as const;
}

/** Kitchen: a glowing tower whose height shows how much food is waiting there. Food orbs circle its crown. */
export function DonorTower({ donor, batches, position, selected, dimmed, showLabel, animate, onSelect, labels }: NodeProps & {
  donor: NetworkDonor;
  batches: NetworkBatch[];
}) {
  const [hovered, bind] = useHover();
  const group = useRef<THREE.Group>(null);
  const height = 0.5 + Math.min(1.3, donor.servingsAvailable / 70 + batches.length * 0.12);
  const color = roleColor.DONOR;
  const active = hovered || selected;
  const anchor = useAnchor(position[0], height + 0.55, position[1]);
  useSceneLabel(labels, `donor-${donor.id}`, anchor, active ? (
    <div className="scene-tooltip">
      <strong>{donor.name}</strong>
      {donor.donorType && donorTypeLabel[donor.donorType]} · {donor.openBatches} open batch{donor.openBatches === 1 ? '' : 'es'}
      <br />
      <span style={{ color }}>{donor.servingsAvailable} servings waiting for a match</span>
    </div>
  ) : showLabel ? (
    <div className="scene-label" data-dim={dimmed}>
      <span className="dot" style={{ background: color }} />
      {donor.name}
    </div>
  ) : null, active);

  useFrame((_, dt) => {
    if (!group.current) return;
    group.current.scale.setScalar(THREE.MathUtils.damp(group.current.scale.x, active ? 1.12 : 1, 8, dt));
  });

  return (
    <group position={[position[0], 0, position[1]]}>
      <group ref={group} {...bind} onClick={(e) => { e.stopPropagation(); onSelect({ kind: 'donor', id: donor.id }); }}>
        <mesh position-y={0.04}>
          <cylinderGeometry args={[0.42, 0.48, 0.08, 6]} />
          <meshStandardMaterial color="#f1dcc4" emissive={color} emissiveIntensity={active ? 0.2 : 0.04} roughness={0.8} />
        </mesh>
        <mesh position-y={height / 2 + 0.08}>
          <boxGeometry args={[0.36, height, 0.36]} />
          <meshStandardMaterial color="#f4a85c" emissive={color} emissiveIntensity={dimmed ? 0 : active ? 0.3 : 0.08} roughness={0.55} metalness={0.05} transparent opacity={dimmed ? 0.35 : 1} />
        </mesh>
        <mesh position-y={height + 0.1}>
          <boxGeometry args={[0.4, 0.05, 0.4]} />
          <meshBasicMaterial color={color} toneMapped={false} transparent opacity={dimmed ? 0.3 : 1} />
        </mesh>
        {/* light beam when food is waiting */}
        {donor.servingsAvailable > 0 && !dimmed && (
          <mesh position-y={height + 0.9}>
            <cylinderGeometry args={[0.015, 0.09, 1.6, 12, 1, true]} />
            <meshBasicMaterial color={color} transparent opacity={0.22} toneMapped={false} side={THREE.DoubleSide} />
          </mesh>
        )}
      </group>
      <FoodOrbs batches={batches} height={height} animate={animate} dimmed={dimmed} />
    </group>
  );
}

function FoodOrbs({ batches, height, animate, dimmed }: { batches: NetworkBatch[]; height: number; animate: boolean; dimmed: boolean }) {
  const group = useRef<THREE.Group>(null);
  useFrame(({ clock }) => {
    if (!group.current || !animate) return;
    group.current.rotation.y = clock.elapsedTime * 0.35;
    group.current.children.forEach((c, i) => {
      c.position.y = height + 0.35 + Math.sin(clock.elapsedTime * 1.6 + i * 1.7) * 0.08;
    });
  });
  if (dimmed) return null;
  // the most urgent few are enough to read the state; dozens of orbs would just blur together
  const shown = [...batches].sort((x, y) => x.minutesLeft - y.minutesLeft).slice(0, 6);
  return (
    <group ref={group}>
      {shown.map((b, i) => {
        const a = (i / Math.max(1, shown.length)) * Math.PI * 2;
        const r = 0.62;
        const size = 0.05 + Math.min(0.05, b.remainingServings / 900);
        const color = freshnessColor(b.freshness);
        return (
          <mesh key={b.id} position={[Math.cos(a) * r, height + 0.35, Math.sin(a) * r]}>
            <sphereGeometry args={[size, 20, 20]} />
            <meshBasicMaterial color={color} toneMapped={false} />
          </mesh>
        );
      })}
    </group>
  );
}

/** Recipient: a dome with a ring showing today's remaining capacity; critical need pulses. */
export function RecipientHub({ recipient, position, selected, dimmed, showLabel, animate, onSelect, labels }: NodeProps & {
  recipient: NetworkRecipient;
}) {
  const [hovered, bind] = useHover();
  const pulse = useRef<THREE.Mesh>(null);
  const dome = useRef<THREE.Group>(null);
  const color = roleColor.RECIPIENT;
  const need = recipient.needLevel ?? 'NORMAL';
  const capacity = recipient.dailyCapacity ?? 1;
  const filled = 1 - Math.max(0, Math.min(1, recipient.remainingToday / capacity));
  const active = hovered || selected;
  const ring = useMemo(() => Math.max(0.0001, filled) * Math.PI * 2, [filled]);
  const anchor = useAnchor(position[0], 0.95, position[1]);
  useSceneLabel(labels, `recipient-${recipient.id}`, anchor, active ? (
    <div className="scene-tooltip">
      <strong>{recipient.name}</strong>
      {recipient.recipientType && recipientTypeLabel[recipient.recipientType]} ·{' '}
      <span style={{ color: needColor[need] }}>{needLabel[need]}</span>
      <br />
      Room for {recipient.remainingToday} of {capacity} servings today
    </div>
  ) : showLabel ? (
    <div className="scene-label" data-dim={dimmed}>
      <span className="dot" style={{ background: color }} />
      {recipient.name}
    </div>
  ) : null, active);

  useFrame(({ clock }, dt) => {
    if (pulse.current && animate && need !== 'NORMAL') {
      const speed = need === 'CRITICAL' ? 1.4 : 0.8;
      const t = (clock.elapsedTime * speed) % 1;
      pulse.current.scale.setScalar(1 + t * 1.6);
      (pulse.current.material as THREE.MeshBasicMaterial).opacity = 0.55 * (1 - t);
    }
    if (dome.current) {
      dome.current.scale.setScalar(THREE.MathUtils.damp(dome.current.scale.x, active ? 1.15 : 1, 8, dt));
    }
  });

  return (
    <group position={[position[0], 0, position[1]]}>
      <group ref={dome} {...bind} onClick={(e) => { e.stopPropagation(); onSelect({ kind: 'recipient', id: recipient.id }); }}>
        <mesh position-y={0}>
          <sphereGeometry args={[0.42, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2]} />
          <meshStandardMaterial color="#f4a3b3" emissive={color} emissiveIntensity={dimmed ? 0 : active ? 0.3 : 0.08} roughness={0.45} metalness={0.05} transparent opacity={dimmed ? 0.35 : 0.95} />
        </mesh>
        <mesh position-y={0.47}>
          <sphereGeometry args={[0.06, 16, 16]} />
          <meshBasicMaterial color={needColor[need]} toneMapped={false} />
        </mesh>
      </group>
      {/* capacity used today */}
      <mesh rotation-x={-Math.PI / 2} position-y={0.02}>
        <ringGeometry args={[0.55, 0.62, 64]} />
        <meshBasicMaterial color="#ecd3d6" transparent opacity={0.8} />
      </mesh>
      <mesh rotation-x={-Math.PI / 2} rotation-z={Math.PI / 2} position-y={0.025}>
        <ringGeometry args={[0.55, 0.62, 64, 1, 0, ring]} />
        <meshBasicMaterial color={color} toneMapped={false} transparent opacity={dimmed ? 0.3 : 0.9} />
      </mesh>
      {need !== 'NORMAL' && !dimmed && (
        <mesh ref={pulse} rotation-x={-Math.PI / 2} position-y={0.03}>
          <ringGeometry args={[0.62, 0.66, 64]} />
          <meshBasicMaterial color={needColor[need]} transparent opacity={0.5} toneMapped={false} />
        </mesh>
      )}
    </group>
  );
}

/** Idle volunteer at home: a small floating crystal. */
export function VolunteerMarker({ volunteer, position, selected, animate, onSelect, labels }: Omit<NodeProps, 'dimmed' | 'showLabel'> & {
  volunteer: NetworkVolunteer;
}) {
  const [hovered, bind] = useHover();
  const ref = useRef<THREE.Mesh>(null);
  const active = hovered || selected;
  const anchor = useAnchor(position[0], 0.75, position[1]);
  useSceneLabel(labels, `volunteer-${volunteer.id}`, anchor, active ? (
    <div className="scene-tooltip">
      <strong>{volunteer.name}</strong>
      {volunteer.vehicle && vehicleLabel[volunteer.vehicle]} · {volunteer.activePickups} active pickup{volunteer.activePickups === 1 ? '' : 's'}
    </div>
  ) : null, true);
  useFrame(({ clock }) => {
    if (!ref.current) return;
    if (animate) {
      ref.current.rotation.y = clock.elapsedTime * 0.8;
      ref.current.position.y = 0.32 + Math.sin(clock.elapsedTime * 2 + position[0]) * 0.05;
    }
  });
  return (
    <group position={[position[0], 0, position[1]]}>
      <mesh ref={ref} position-y={0.32} {...bind} onClick={(e) => { e.stopPropagation(); onSelect({ kind: 'volunteer', id: volunteer.id }); }}>
        <octahedronGeometry args={[active ? 0.16 : 0.12]} />
        <meshBasicMaterial color={roleColor.VOLUNTEER} toneMapped={false} />
      </mesh>
    </group>
  );
}
