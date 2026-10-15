import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { connectEvents, type ConnectionState } from '../services/events';
import type { LiveEvent } from '../types';
import { useAuth } from './AuthContext';

interface LiveState {
  state: ConnectionState;
  events: LiveEvent[];
  unread: number;
  markRead: () => void;
}

const LiveContext = createContext<LiveState | null>(null);

/** Which cached queries an event makes stale. Keeps every screen in sync without polling. */
const INVALIDATES: Record<string, string[][]> = {
  BATCH_LOGGED: [['batches'], ['network'], ['impact'], ['me']],
  BATCH_EXPIRED: [['batches'], ['network'], ['dispatches'], ['impact'], ['me']],
  BATCH_WITHDRAWN: [['batches'], ['network'], ['dispatches']],
  DISPATCH_ASSIGNED: [['dispatches'], ['pickups'], ['network'], ['batches']],
  DISPATCH_CLAIMED: [['dispatches'], ['pickups'], ['network']],
  DISPATCH_RELEASED: [['dispatches'], ['pickups'], ['network']],
  DISPATCH_PICKED_UP: [['dispatches'], ['network']],
  DISPATCH_DELIVERED: [['dispatches'], ['network'], ['batches'], ['impact'], ['me'], ['stats']],
  DISPATCH_CONFIRMED: [['dispatches'], ['impact'], ['me']],
  DISPATCH_CANCELLED: [['dispatches'], ['pickups'], ['network'], ['batches']],
  MATCHING_RUN: [['dispatches'], ['network'], ['batches']],
  USER_REGISTERED: [['users']],
  USER_STATUS_CHANGED: [['users'], ['me'], ['network']],
  PROFILE_UPDATED: [['users'], ['me'], ['network']],
  SETTINGS_CHANGED: [['matching-settings']],
};

/** Events worth a pop-up toast (the rest only go to the live feed). */
const TOAST: Record<string, 'success' | 'info' | 'warning'> = {
  DISPATCH_DELIVERED: 'success',
  DISPATCH_ASSIGNED: 'info',
  BATCH_EXPIRED: 'warning',
  USER_STATUS_CHANGED: 'info',
  DISPATCH_CANCELLED: 'warning',
};

export function LiveProvider({ children }: { children: ReactNode }) {
  const { token, refresh, user } = useAuth();
  const queryClient = useQueryClient();
  const [state, setState] = useState<ConnectionState>('offline');
  // the feed is tagged with the session it belongs to, so signing out hides it without an extra state reset
  const [feed, setFeed] = useState<{ token: string | null; events: LiveEvent[]; unread: number }>({ token, events: [], unread: 0 });
  const userIdRef = useRef<string | undefined>(user?.id);
  useEffect(() => {
    userIdRef.current = user?.id;
  }, [user?.id]);

  const current = feed.token === token ? feed : { token, events: [] as LiveEvent[], unread: 0 };
  const events = current.events;
  const unread = current.unread;

  useEffect(() => {
    if (!token) return;
    const close = connectEvents({
      token,
      onState: setState,
      onEvent: (e) => {
        setFeed((prev) => {
          const base = prev.token === token ? prev : { token, events: [], unread: 0 };
          return { token, events: [e, ...base.events.filter((p) => p.id !== e.id)].slice(0, 60), unread: base.unread + 1 };
        });
        for (const key of INVALIDATES[e.type] ?? [['network']]) {
          queryClient.invalidateQueries({ queryKey: key });
        }
        if (e.type === 'USER_STATUS_CHANGED' && e.entityId === userIdRef.current) {
          refresh().catch(() => undefined);
        }
        const kind = TOAST[e.type];
        if (kind && document.visibilityState === 'visible') {
          toast[kind](e.message, { duration: 4000 });
        }
      },
    });
    return close;
  }, [token, queryClient, refresh]);

  const value = useMemo(
    () => ({ state, events, unread, markRead: () => setFeed((f) => ({ ...f, unread: 0 })) }),
    [state, events, unread],
  );
  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>;
}

// the hook lives next to its provider on purpose; fast refresh simply reloads this one file
// oxlint-disable-next-line react/only-export-components
export function useLive(): LiveState {
  const ctx = useContext(LiveContext);
  if (!ctx) throw new Error('useLive must be used inside <LiveProvider>');
  return ctx;
}
