import { useSyncExternalStore } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import type { LabelStore } from './labelStore';

/** DOM layer that renders every registered label; positions are written by {@link LabelProjector}. */
export function LabelOverlay({ store }: { store: LabelStore }) {
  useSyncExternalStore(store.subscribe, store.getVersion);
  return (
    <div className="pointer-events-none absolute inset-0 isolate overflow-hidden" aria-hidden>
      {[...store.entries].map(([id, e]) => (
        <div
          key={id}
          ref={(el) => {
            if (el) store.elements.set(id, el);
            else store.elements.delete(id);
          }}
          style={{ position: 'absolute', left: 0, top: 0, visibility: 'hidden', zIndex: e.front ? 2 : 1, willChange: 'transform' }}
        >
          {e.node}
        </div>
      ))}
    </div>
  );
}

const projected = new THREE.Vector3();

/** Inside the canvas: projects each label anchor to screen space once per frame. */
export function LabelProjector({ store }: { store: LabelStore }) {
  useFrame(({ camera, size }) => {
    for (const [id, entry] of store.entries) {
      const el = store.elements.get(id);
      if (!el) continue;
      projected.copy(entry.position()).project(camera);
      const visible = projected.z < 1 && Math.abs(projected.x) < 1.15 && Math.abs(projected.y) < 1.15;
      el.style.visibility = visible ? 'visible' : 'hidden';
      if (visible) {
        const x = ((projected.x + 1) / 2) * size.width;
        const y = ((1 - projected.y) / 2) * size.height;
        el.style.transform = `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0)`;
      }
    }
  });
  return null;
}
