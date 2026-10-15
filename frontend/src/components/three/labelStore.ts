import { useEffect, type ReactNode } from 'react';
import type * as THREE from 'three';

/**
 * Screen-space labels for the 3D scene.
 *
 * drei's <Html> mounts a separate React root per label; tearing those roots down while a page transition is
 * rendering crashed React ("removeChild ... not a child of this node"). Instead, scene objects register their label
 * here, a plain DOM overlay in the main React tree renders them, and a projector inside the canvas moves each one
 * to its object's screen position every frame. One React root, no race.
 */
export interface LabelEntry {
  /** World-space anchor, read every frame (so it can follow moving objects). */
  position: () => THREE.Vector3;
  node: ReactNode;
  /** Draw above ordinary labels (hover tooltips). */
  front?: boolean;
}

export class LabelStore {
  readonly entries = new Map<string, LabelEntry>();
  readonly elements = new Map<string, HTMLDivElement>();
  private readonly listeners = new Set<() => void>();
  private version = 0;

  set(id: string, entry: LabelEntry) {
    this.entries.set(id, entry);
    this.changed();
  }

  delete(id: string) {
    if (this.entries.delete(id)) this.changed();
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getVersion = () => this.version;

  private changed() {
    this.version++;
    this.listeners.forEach((l) => l());
  }
}

/** Shows {@code node} at {@code position} while it is non-null; removes the label on unmount. */
export function useSceneLabel(store: LabelStore, id: string, position: () => THREE.Vector3, node: ReactNode | null, front = false) {
  useEffect(() => {
    if (node == null) {
      store.delete(id);
      return;
    }
    store.set(id, { position, node, front });
  }, [store, id, position, node, front]);

  useEffect(() => () => store.delete(id), [store, id]);
}
