import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { motion } from 'motion/react';
import { clsx } from 'clsx';
import { ArrowRight, KeyRound, Mail } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Field';
import { errorMessage } from '../lib/errors';
import { Logo } from '../components/Navbar';
import Aurora from '../components/reactbits/Aurora';
import BlurText from '../components/reactbits/BlurText';
import { roleColor } from '../lib/theme';
import { roleLabel } from '../lib/format';
import type { Role } from '../types';

const DEMO: { role: Role; email: string; who: string }[] = [
  { role: 'ADMIN', email: 'admin@sevasync.demo', who: 'Campus Coordinator' },
  { role: 'DONOR', email: 'mess@sevasync.demo', who: 'Boys Hostel Mess' },
  { role: 'VOLUNTEER', email: 'arjun@sevasync.demo', who: 'Arjun (bicycle)' },
  { role: 'RECIPIENT', email: 'asha@sevasync.demo', who: 'Asha Bhavan Orphanage' },
];
const DEMO_PASSWORD = 'Demo@2026';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e?: FormEvent, override?: { email: string; password: string }) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(override?.email ?? email, override?.password ?? password);
      const to = (location.state as { from?: string } | null)?.from ?? '/app';
      navigate(to, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={<>New here? <Link to="/register" className="font-semibold text-saffron-600 hover:underline">Create an account</Link></>}
    >
      <form onSubmit={submit} className="space-y-4">
        <Input label="E-mail" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@campus.edu" />
        <Input label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
        {error && <p className="rounded-xl border border-rose-400/20 bg-rose-500/10 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
        <Button type="submit" size="lg" className="w-full" loading={busy} trailing={<ArrowRight className="size-4" />}>
          Sign in
        </Button>
      </form>

      <div className="mt-8">
        <div className="flex items-center gap-3 text-[11px] uppercase tracking-[0.18em] text-ink-500">
          <span className="h-px flex-1 bg-wash/8" /> Exhibition demo accounts <span className="h-px flex-1 bg-wash/8" />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2">
          {DEMO.map((d) => (
            <button
              key={d.email}
              type="button"
              disabled={busy}
              onClick={() => {
                setEmail(d.email);
                setPassword(DEMO_PASSWORD);
                submit(undefined, { email: d.email, password: DEMO_PASSWORD });
              }}
              className="group rounded-2xl border hairline bg-wash/[0.02] px-3 py-2.5 text-left transition hover:bg-wash/[0.05]"
            >
              <span className="text-xs font-semibold" style={{ color: roleColor[d.role] }}>{roleLabel[d.role]}</span>
              <span className="block truncate text-xs text-ink-400 group-hover:text-ink-200">{d.who}</span>
            </button>
          ))}
        </div>
        <p className="mt-3 text-center text-[11px] text-ink-500">
          Available when the server runs with <span className="kbd">--demo</span> · password <span className="kbd">{DEMO_PASSWORD}</span>
        </p>
      </div>
    </AuthLayout>
  );
}

/** Shared split layout for sign-in and sign-up: aurora brand panel on the left, form on the right. */
export function AuthLayout({ title, subtitle, children, wide }: {
  title: string;
  subtitle?: React.ReactNode;
  children: React.ReactNode;
  wide?: boolean;
}) {
  return (
    <div className="grid min-h-dvh grid-cols-1 bg-night-900 lg:grid-cols-[1.05fr_1fr]">
      <div className="relative hidden overflow-hidden border-r hairline lg:block">
        {/* warm painted backdrop that shows even without WebGL; the Aurora adds slow moving light on top */}
        <div
          className="absolute inset-0"
          style={{
            background:
              'radial-gradient(60% 50% at 20% 15%, #ffd6a8 0%, transparent 70%),' +
              'radial-gradient(55% 45% at 85% 35%, #ffc2cc 0%, transparent 70%),' +
              'radial-gradient(70% 55% at 45% 90%, #ffe7a3 0%, transparent 70%),' +
              'linear-gradient(160deg, #fff3e4, #fde3cf)',
          }}
        />
        <div className="absolute inset-0 opacity-60 mix-blend-multiply">
          <Aurora colorStops={['#f7b267', '#fb7185', '#fcd34d']} amplitude={1.2} blend={0.6} speed={0.5} />
        </div>
        <div className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-night-900/85 to-transparent" />
        <div className="relative flex h-full flex-col justify-between p-12">
          <Logo />
          <div className="max-w-md">
            <div className="font-display text-4xl font-semibold leading-tight tracking-tight text-ink-100">
              <BlurText text="Food that would have been thrown away is someone's next meal." delay={60} animateBy="words" trigger="load" />
            </div>
            <div className="mt-8 flex gap-6 text-sm text-ink-300">
              {(['DONOR', 'VOLUNTEER', 'RECIPIENT'] as Role[]).map((r) => (
                <span key={r} className="flex items-center gap-2">
                  <span className="size-2 rounded-full" style={{ background: roleColor[r] }} /> {roleLabel[r]}s
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-center px-5 py-12">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
          className={clsx('w-full min-w-0', wide ? 'max-w-xl' : 'max-w-md')}
        >
          <div className="mb-8 lg:hidden"><Logo /></div>
          <div className="mb-7 flex items-center gap-3">
            <span className="grid size-11 place-items-center rounded-2xl bg-wash/5 text-saffron-600">
              {wide ? <Mail className="size-5" /> : <KeyRound className="size-5" />}
            </span>
            <div>
              <h1 className="text-2xl font-semibold">{title}</h1>
              {subtitle && <p className="mt-0.5 text-sm text-ink-400">{subtitle}</p>}
            </div>
          </div>
          {children}
        </motion.div>
      </div>
    </div>
  );
}
