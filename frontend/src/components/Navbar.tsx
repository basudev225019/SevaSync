import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { motion } from 'motion/react';
import { clsx } from 'clsx';
import { useAuth } from '../context/AuthContext';
import { Button } from './ui/Button';
import Magnet from './reactbits/Magnet';

export function Logo({ compact }: { compact?: boolean }) {
  // unique per instance: a gradient id defined inside a hidden copy of the logo would not render elsewhere
  const gid = `logo-${useId().replace(/:/g, '')}`;
  return (
    <Link to="/" className="group inline-flex items-center gap-2.5" aria-label="SevaSync home">
      <span className="relative grid size-9 place-items-center rounded-xl bg-gradient-to-br from-saffron-400/25 to-leaf-400/15 ring-1 ring-wash/10">
        <svg viewBox="0 0 64 64" className="size-6" aria-hidden>
          <defs>
            <linearGradient id={gid} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="#e8862a" />
              <stop offset="1" stopColor="#1f9d55" />
            </linearGradient>
          </defs>
          <path d="M18 38c0-9 6.5-16 14-16s14 7 14 16" fill="none" stroke={`url(#${gid})`} strokeWidth="5" strokeLinecap="round" />
          <path d="M14 42h36" stroke={`url(#${gid})`} strokeWidth="5" strokeLinecap="round" />
          <circle cx="32" cy="15" r="4" fill="#e8862a" />
        </svg>
      </span>
      {!compact && (
        <span className="font-display text-lg font-semibold tracking-tight text-ink-100">
          Seva<span className="text-gradient">Sync</span>
        </span>
      )}
    </Link>
  );
}

/** Floating public navigation that turns into a glass bar once the page scrolls. */
export function Navbar() {
  const { user } = useAuth();
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 24);
    on();
    window.addEventListener('scroll', on, { passive: true });
    return () => window.removeEventListener('scroll', on);
  }, []);

  return (
    <motion.header
      initial={{ y: -40, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
      className="fixed inset-x-0 top-0 z-50 flex justify-center px-4 pt-4"
    >
      <div className={clsx('flex w-full max-w-6xl items-center justify-between rounded-2xl px-4 py-2.5 transition-all duration-500', scrolled ? 'glass-strong shadow-2xl' : 'bg-transparent')}>
        <Logo />
        <nav className="hidden items-center gap-1 md:flex">
          {[
            ['#how', 'How it works'],
            ['#roles', 'Who it serves'],
            ['#impact', 'Impact'],
            ['#trust', 'Trust'],
          ].map(([href, label]) => (
            <a key={href} href={href} className="rounded-xl px-3 py-2 text-sm text-ink-300 transition hover:text-ink-100">
              {label}
            </a>
          ))}
        </nav>
        <div className="flex items-center gap-2">
          {user ? (
            <Link to="/app"><Button size="sm">Open dashboard</Button></Link>
          ) : (
            <>
              <Link to="/login" className="hidden sm:block"><Button size="sm" variant="ghost">Sign in</Button></Link>
              <Magnet padding={40} magnetStrength={4}>
                <Link to="/register"><Button size="sm">Join SevaSync</Button></Link>
              </Magnet>
            </>
          )}
        </div>
      </div>
    </motion.header>
  );
}
