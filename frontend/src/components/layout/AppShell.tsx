import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { AnimatePresence, motion } from 'motion/react';
import { clsx } from 'clsx';
import { Bell, LogOut, Menu, X } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useLive } from '../../context/LiveContext';
import { NAV } from '../../lib/nav';
import { roleLabel } from '../../lib/format';
import { roleColor } from '../../lib/theme';
import { Avatar } from '../ui/Avatar';
import { Logo } from '../Navbar';
import { LiveFeed } from './LiveFeed';

/** Signed-in frame: role-aware sidebar, live status, activity drawer and animated page transitions. */
export function AppShell() {
  const { user, logout } = useAuth();
  const { state, unread, markRead } = useLive();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileNav, setMobileNav] = useState(false);
  const [feedOpen, setFeedOpen] = useState(false);

  // Escape closes the mobile drawer and the activity panel
  useEffect(() => {
    if (!mobileNav && !feedOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      setMobileNav(false);
      setFeedOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mobileNav, feedOpen]);

  if (!user) return null;
  const items = NAV[user.role].filter((n) => !n.activeOnly || user.status === 'ACTIVE');
  const fullBleed = location.pathname === '/app/network';

  const nav = (
    <nav className="flex flex-col gap-1">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/app'}
          onClick={() => setMobileNav(false)}
          className={({ isActive }) =>
            clsx(
              'group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
              isActive ? 'text-ink-100' : 'text-ink-400 hover:bg-wash/[0.04] hover:text-ink-200',
            )
          }
        >
          {({ isActive }) => (
            <>
              {isActive && (
                <motion.span
                  layoutId="nav-active"
                  className="absolute inset-0 rounded-xl bg-wash/[0.07] ring-1 ring-wash/10"
                  transition={{ type: 'spring', stiffness: 500, damping: 40 }}
                />
              )}
              <span className="relative" style={isActive ? { color: roleColor[user.role] } : undefined}>{item.icon}</span>
              <span className="relative">{item.label}</span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );

  const signOut = async () => {
    await logout();
    navigate('/');
  };

  return (
    <div className="relative min-h-dvh bg-night-900">
      <div className="pointer-events-none fixed inset-0 -z-0 opacity-60">
        <div className="absolute -left-40 -top-40 size-[520px] rounded-full blur-[140px]" style={{ background: `${roleColor[user.role]}14` }} />
        <div className="absolute -bottom-40 right-0 size-[480px] rounded-full bg-leaf-500/5 blur-[140px]" />
      </div>

      {/* sidebar (desktop) */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 flex-col border-r hairline bg-night-900/80 px-4 py-5 backdrop-blur-xl lg:flex">
        <div className="px-2"><Logo /></div>
        <div className="mt-8 flex-1 overflow-y-auto">{nav}</div>
        <UserCard onSignOut={signOut} />
      </aside>

      {/* mobile drawer */}
      <AnimatePresence>
        {mobileNav && (
          <motion.div className="fixed inset-0 z-50 lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <div className="absolute inset-0 bg-ink-100/25 backdrop-blur-sm" onClick={() => setMobileNav(false)} />
            <motion.aside
              className="glass-strong absolute inset-y-0 left-0 flex w-72 flex-col px-4 py-5"
              initial={{ x: -300 }}
              animate={{ x: 0 }}
              exit={{ x: -300 }}
              transition={{ type: 'spring', stiffness: 380, damping: 36 }}
            >
              <div className="flex items-center justify-between px-2">
                <Logo />
                <button onClick={() => setMobileNav(false)} className="grid size-9 place-items-center rounded-xl text-ink-400 hover:bg-wash/5" aria-label="Close menu">
                  <X className="size-4" />
                </button>
              </div>
              <div className="mt-8 min-h-0 flex-1 overflow-y-auto">{nav}</div>
              <UserCard onSignOut={signOut} />
            </motion.aside>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="relative lg:pl-64">
        {/* top bar */}
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b hairline bg-night-900/70 px-4 backdrop-blur-xl sm:px-6">
          <button onClick={() => setMobileNav(true)} className="grid size-9 place-items-center rounded-xl text-ink-300 hover:bg-wash/5 lg:hidden" aria-label="Open menu">
            <Menu className="size-5" />
          </button>
          <div className="lg:hidden"><Logo compact /></div>
          <div className="flex-1" />
          <ConnectionBadge state={state} />
          <button
            onClick={() => {
              setFeedOpen((o) => !o);
              markRead();
            }}
            className="relative grid size-10 place-items-center rounded-xl text-ink-300 transition hover:bg-wash/5 hover:text-ink-100"
            aria-label="Live activity"
          >
            <Bell className="size-[18px]" />
            {unread > 0 && (
              <span className="absolute right-1.5 top-1.5 grid min-w-4 place-items-center rounded-full bg-saffron-500 px-1 text-[10px] font-bold text-white">
                {unread > 9 ? '9+' : unread}
              </span>
            )}
          </button>
          <div className="hidden items-center gap-2.5 sm:flex">
            <Avatar name={user.name} role={user.role} size={34} />
          </div>
        </header>

        <AnimatePresence>
          {feedOpen && (
            <motion.div
              className="glass-strong fixed right-3 top-[72px] z-40 w-[min(380px,calc(100vw-24px))] rounded-3xl p-3"
              initial={{ opacity: 0, y: -10, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.97 }}
            >
              <div className="flex items-center justify-between px-3 pb-2 pt-1">
                <h3 className="text-sm font-semibold">Live activity</h3>
                <button onClick={() => setFeedOpen(false)} className="text-ink-400 hover:text-ink-100" aria-label="Close">
                  <X className="size-4" />
                </button>
              </div>
              <div className="max-h-[60dvh] overflow-y-auto pr-1">
                <LiveFeed />
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        <main className={clsx('relative', fullBleed ? '' : 'mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:py-8')}>
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 12, filter: 'blur(6px)' }}
              animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
              exit={{ opacity: 0, y: -8, filter: 'blur(4px)' }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}

function UserCard({ onSignOut }: { onSignOut: () => void }) {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="mt-4 flex items-center gap-3 rounded-2xl border hairline bg-wash/[0.02] p-3">
      <Avatar name={user.name} role={user.role} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-ink-100">{user.name}</p>
        <p className="text-xs" style={{ color: roleColor[user.role] }}>{roleLabel[user.role]}</p>
      </div>
      <button onClick={onSignOut} className="grid size-9 place-items-center rounded-xl text-ink-400 transition hover:bg-wash/5 hover:text-rose-600" aria-label="Sign out" title="Sign out">
        <LogOut className="size-4" />
      </button>
    </div>
  );
}

function ConnectionBadge({ state }: { state: string }) {
  const live = state === 'live';
  return (
    <span className={clsx('hidden items-center gap-2 rounded-full px-3 py-1 text-xs font-medium sm:inline-flex', live ? 'bg-leaf-500/10 text-leaf-700' : 'bg-wash/5 text-ink-400')}>
      <span className="relative inline-flex size-2">
        {live && <span className="absolute inset-0 animate-ping rounded-full bg-leaf-400 opacity-60" />}
        <span className={clsx('relative inline-flex size-2 rounded-full', live ? 'bg-leaf-400' : 'bg-ink-500')} />
      </span>
      {live ? 'Live' : state === 'reconnecting' ? 'Reconnecting…' : 'Connecting…'}
    </span>
  );
}
