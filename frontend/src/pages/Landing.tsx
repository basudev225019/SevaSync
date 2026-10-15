import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { useGSAP } from '@gsap/react';
import Lenis from 'lenis';
import {
  ArrowRight,
  BadgeCheck,
  Bike,
  BrainCircuit,
  Clock3,
  Fingerprint,
  HandHeart,
  HeartHandshake,
  Leaf,
  MapPinned,
  PackagePlus,
  Scale,
  ShieldCheck,
  Sprout,
  Truck,
  Users,
  UtensilsCrossed,
} from 'lucide-react';
import { api } from '../services/api';
import { CampusScene } from '../components/three/CampusScene';
import { Navbar, Logo } from '../components/Navbar';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import SplitText from '../components/reactbits/SplitText';
import BlurText from '../components/reactbits/BlurText';
import RotatingText from '../components/reactbits/RotatingText';
import CountUp from '../components/reactbits/CountUp';
import ShinyText from '../components/reactbits/ShinyText';
import StarBorder from '../components/reactbits/StarBorder';
import DecryptedText from '../components/reactbits/DecryptedText';
import Aurora from '../components/reactbits/Aurora';
import ClickSpark from '../components/reactbits/ClickSpark';
import FadeContent from '../components/reactbits/FadeContent';
import { useReducedMotion } from '../hooks/useReducedMotion';
import { roleColor } from '../lib/theme';

gsap.registerPlugin(ScrollTrigger);

export default function Landing() {
  const reduced = useReducedMotion();
  const stats = useQuery({ queryKey: ['stats'], queryFn: api.publicStats, refetchInterval: 20_000 });
  const network = useQuery({ queryKey: ['network', 'public'], queryFn: api.publicNetwork, refetchInterval: 8_000 });

  // buttery smooth scrolling, kept in sync with GSAP ScrollTrigger
  useEffect(() => {
    if (reduced) return;
    const lenis = new Lenis({ lerp: 0.1, smoothWheel: true });
    lenis.on('scroll', ScrollTrigger.update);
    const tick = (t: number) => lenis.raf(t * 1000);
    gsap.ticker.add(tick);
    gsap.ticker.lagSmoothing(0);
    return () => {
      gsap.ticker.remove(tick);
      lenis.destroy();
    };
  }, [reduced]);

  return (
    <ClickSpark sparkColor={['#fbbf24', '#34d399', '#fb7185']} sparkCount={10} sparkRadius={22} duration={500}>
      <div className="relative overflow-x-clip bg-night-900">
        <Navbar />
        <Hero stats={stats.data} network={network.data} />
        <ProblemStrip />
        <HowItWorks />
        <Roles />
        <SmartMatching />
        <Trust />
        <FinalCta />
        <Footer />
      </div>
    </ClickSpark>
  );
}

// ------------------------------------------------------------------ hero

function Hero({ stats, network }: { stats?: Awaited<ReturnType<typeof api.publicStats>>; network?: Awaited<ReturnType<typeof api.publicNetwork>> }) {
  return (
    <section className="relative min-h-[100svh] overflow-hidden">
      {/* live 3D network of the real platform */}
      <div className="absolute inset-0">
        {network ? (
          <CampusScene network={network} mode="hero" className="h-full w-full" showLabels={false} />
        ) : (
          <div className="grid-fade absolute inset-0" />
        )}
      </div>
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-night-900 via-night-900/80 to-night-900/10" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-56 bg-gradient-to-t from-night-900 to-transparent" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-night-900/80 to-transparent" />

      <div className="pointer-events-none relative z-10 mx-auto flex min-h-[100svh] max-w-6xl flex-col justify-center px-5 pb-28 pt-32">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, duration: 0.6 }}
          className="pointer-events-auto mb-6 inline-flex w-fit items-center gap-2.5 rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-1.5 text-xs font-medium text-ink-200 backdrop-blur"
        >
          <span className="relative inline-flex size-2">
            <span className="absolute inset-0 animate-ping rounded-full bg-leaf-400 opacity-70" />
            <span className="relative inline-flex size-2 rounded-full bg-leaf-400" />
          </span>
          <ShinyText text="Live on campus right now" speed={3} color="#b7bfce" shineColor="#ffffff" />
        </motion.div>

        <h1 className="max-w-3xl font-display text-[clamp(2.6rem,7vw,5.6rem)] font-semibold leading-[0.95] tracking-[-0.035em]">
          <SplitText text="Every meal," tag="span" className="block" textAlign="left" delay={35} duration={1} trigger="load" />
          <span className="block text-gradient">
            <SplitText text="rescued in time." tag="span" textAlign="left" delay={35} duration={1} trigger="load" />
          </span>
        </h1>

        <div className="mt-7 max-w-xl text-[17px] leading-relaxed text-ink-300">
          <BlurText
            text="Hostel messes, canteens and fests cook more than they serve. SevaSync checks the food is safe, matches it to the shelter that needs it most, and puts a student volunteer on the road, all before it expires."
            delay={18}
            animateBy="words"
            direction="bottom"
            trigger="load"
          />
        </div>

        <div className="mt-6 flex items-center gap-2 text-sm text-ink-400">
          Built for
          <RotatingText
            texts={['hostel messes', 'orphanages', 'student volunteers', 'night shelters', 'campus canteens']}
            mainClassName="overflow-hidden rounded-lg bg-saffron-400/12 px-2 py-0.5 font-semibold text-saffron-300"
            staggerDuration={0.02}
            rotationInterval={2200}
            splitBy="characters"
          />
        </div>

        <div className="pointer-events-auto mt-9 flex flex-wrap items-center gap-3">
          <Link to="/register">
            <StarBorder as="span" color="#fbbf24" duration={5} className="inline-block" backgroundColor="#0e121b" textColor="#f4f6fb">
              <span className="flex items-center gap-2 px-1 font-semibold">
                Join the movement <ArrowRight className="size-4" />
              </span>
            </StarBorder>
          </Link>
          <Link to="/login">
            <Button variant="secondary" size="lg">Sign in</Button>
          </Link>
        </div>

        <div className="pointer-events-auto mt-14 grid max-w-3xl grid-cols-2 gap-3 sm:grid-cols-4">
          <HeroStat value={stats?.mealsServed} label="meals served" />
          <HeroStat value={stats?.foodSavedKg} label="kg food rescued" />
          <HeroStat value={stats?.co2AvoidedKg} label="kg CO₂e avoided" />
          <HeroStat value={stats ? stats.donors + stats.recipients + stats.volunteers : undefined} label="active partners" />
        </div>
      </div>

      <div className="pointer-events-none absolute bottom-8 right-8 z-10 hidden max-w-xs text-right text-xs text-ink-500 lg:block">
        <span className="text-ink-300">This is the real network.</span> Kitchens glow amber, shelters rose. Arcs are live
        matches and moving lights are volunteers on their way.
      </div>
    </section>
  );
}

function HeroStat({ value, label }: { value?: number; label: string }) {
  return (
    <div className="glass rounded-2xl px-4 py-3">
      <div className="font-display text-2xl font-semibold text-ink-100 tabular">
        {value == null ? '–' : <CountUp to={Math.round(value)} duration={2} separator="," trigger="load" />}
      </div>
      <div className="mt-0.5 text-xs text-ink-400">{label}</div>
    </div>
  );
}

// ------------------------------------------------------------------ problem

function ProblemStrip() {
  const facts = [
    { icon: <UtensilsCrossed className="size-5" />, big: '≈ 1/3', text: 'of all food produced worldwide is lost or wasted (FAO estimate). Campus kitchens are no exception.' },
    { icon: <Clock3 className="size-5" />, big: '4 hours', text: 'is the safe window SevaSync allows a cooked meal. Phone trees and WhatsApp groups are too slow for that.' },
    { icon: <Leaf className="size-5" />, big: '~2.5 kg', text: 'of CO₂-equivalent avoided per kilo of food eaten instead of landfilled: the planning estimate behind our impact figures.' },
  ];
  return (
    <section className="relative border-y hairline bg-night-850/60 py-16">
      <div className="mx-auto grid max-w-6xl gap-6 px-5 md:grid-cols-3">
        {facts.map((f, i) => (
          <FadeContent key={f.big} blur duration={0.9} delay={i * 0.12} className="flex gap-4">
            <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-white/5 text-saffron-300">{f.icon}</span>
            <div>
              <div className="font-display text-3xl font-semibold text-ink-100">{f.big}</div>
              <p className="mt-1 text-sm leading-relaxed text-ink-400">{f.text}</p>
            </div>
          </FadeContent>
        ))}
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ how it works (GSAP pinned timeline)

const STEPS = [
  { icon: <PackagePlus className="size-6" />, color: roleColor.DONOR, title: 'Log the surplus', text: 'A mess manager logs what is left over: dish, servings, when it was cooked. Thirty seconds on a phone.' },
  { icon: <ShieldCheck className="size-6" />, color: '#34d399', title: 'Safety check', text: 'Category limits (4 h for cooked meals, 12 h for bakery…) and a 30-minute minimum window. Unsafe food never enters the system.' },
  { icon: <BrainCircuit className="size-6" />, color: roleColor.ADMIN, title: 'Smart match', text: 'Soonest-expiring food first. Each batch goes to the reachable shelter with the best mix of need, distance and fairness.' },
  { icon: <Bike className="size-6" />, color: roleColor.VOLUNTEER, title: 'Deliver & confirm', text: 'The nearest volunteer claims the pickup, carries it over and the shelter confirms with a rating. Every step is on the audit trail.' },
];

function HowItWorks() {
  const root = useRef<HTMLElement>(null);
  const reduced = useReducedMotion();

  useGSAP(
    () => {
      if (reduced) return;
      const mm = gsap.matchMedia();
      mm.add('(min-width: 900px)', () => {
        const cards = gsap.utils.toArray<HTMLElement>('.how-step');
        const tl = gsap.timeline({
          scrollTrigger: { trigger: root.current, start: 'top top', end: '+=1600', scrub: 0.8, pin: true },
        });
        tl.fromTo('.how-progress', { scaleX: 0 }, { scaleX: 1, ease: 'none', duration: cards.length });
        cards.forEach((card, i) => {
          tl.fromTo(card, { opacity: 0.25, y: 40, scale: 0.94 }, { opacity: 1, y: 0, scale: 1, duration: 0.6, ease: 'power2.out' }, i);
          tl.fromTo(card.querySelector('.how-glow'), { opacity: 0 }, { opacity: 1, duration: 0.5 }, i);
        });
      });
      return () => mm.revert();
    },
    { scope: root, dependencies: [reduced] },
  );

  return (
    <section id="how" ref={root} className="relative flex min-h-[100svh] items-center py-24">
      <div className="mx-auto w-full max-w-6xl px-5">
        <SectionTitle eyebrow="How it works" title="From leftover to lifeline in four steps" />
        <div className="relative mt-14">
          <div className="absolute left-0 right-0 top-9 hidden h-px bg-white/8 md:block">
            <div className="how-progress h-full origin-left bg-gradient-to-r from-saffron-400 via-violet-400 to-sky-400" />
          </div>
          <div className="grid gap-5 md:grid-cols-4">
            {STEPS.map((s, i) => (
              <div key={s.title} className="how-step relative">
                <div className="relative mb-5 grid size-[72px] place-items-center rounded-3xl border hairline bg-night-800" style={{ color: s.color }}>
                  <div className="how-glow absolute inset-0 rounded-3xl opacity-0 blur-xl" style={{ background: `${s.color}55` }} />
                  <span className="relative">{s.icon}</span>
                  <span className="absolute -right-2 -top-2 grid size-6 place-items-center rounded-full bg-night-900 text-[11px] font-bold text-ink-300 ring-1 ring-white/10">{i + 1}</span>
                </div>
                <h3 className="text-lg font-semibold">{s.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-400">{s.text}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ roles

function Roles() {
  const roles = [
    { role: 'DONOR' as const, icon: <UtensilsCrossed className="size-5" />, title: 'Kitchens & event teams', points: ['Log surplus in seconds', 'See exactly where your food went', 'Ratings and an impact certificate'] },
    { role: 'VOLUNTEER' as const, icon: <Bike className="size-5" />, title: 'Student volunteers', points: ['Nearest pickups first', 'One-tap claim, pick up, deliver', 'Track meals carried and km ridden'] },
    { role: 'RECIPIENT' as const, icon: <HandHeart className="size-5" />, title: 'Shelters & NGOs', points: ['Food matched to your daily capacity', 'Live arrival tracking', 'Confirm and rate every delivery'] },
    { role: 'ADMIN' as const, icon: <HeartHandshake className="size-5" />, title: 'Campus coordinators', points: ['Verify partners', 'Tune the matching strategy', 'Audit trail and impact reports'] },
  ];
  return (
    <section id="roles" className="relative py-28">
      <div className="mx-auto max-w-6xl px-5">
        <SectionTitle eyebrow="Who it serves" title="One platform, four ways to help" />
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {roles.map((r, i) => (
            <FadeContent key={r.role} blur duration={0.8} delay={i * 0.1}>
              <Card spotlight={`${roleColor[r.role]}40`} className="h-full">
                <span className="grid size-11 place-items-center rounded-2xl" style={{ color: roleColor[r.role], background: `${roleColor[r.role]}1f` }}>
                  {r.icon}
                </span>
                <h3 className="mt-5 text-lg font-semibold">{r.title}</h3>
                <ul className="mt-3 space-y-2">
                  {r.points.map((p) => (
                    <li key={p} className="flex items-start gap-2 text-sm text-ink-300">
                      <BadgeCheck className="mt-0.5 size-4 shrink-0" style={{ color: roleColor[r.role] }} />
                      {p}
                    </li>
                  ))}
                </ul>
              </Card>
            </FadeContent>
          ))}
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ smart matching

function SmartMatching() {
  const factors = [
    { icon: <Clock3 className="size-5" />, title: 'Time', text: 'Soonest-expiring food is matched first, and only to places a volunteer can reach before it expires.', w: 100, color: '#fbbf24' },
    { icon: <Users className="size-5" />, title: 'Need', text: 'Critical-need shelters weigh three times more than normal ones, so the hungriest are fed first.', w: 85, color: '#fb7185' },
    { icon: <MapPinned className="size-5" />, title: 'Distance', text: 'Shorter trips mean fresher food and more deliveries per volunteer hour.', w: 60, color: '#38bdf8' },
    { icon: <Scale className="size-5" />, title: 'Fairness', text: 'Recipients who received less this week get a boost, so no shelter is starved by its location.', w: 45, color: '#34d399' },
  ];
  return (
    <section id="impact" className="relative overflow-hidden py-28">
      <div className="grid-fade pointer-events-none absolute inset-0 opacity-60" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-5 lg:grid-cols-2">
        <div>
          <SectionTitle eyebrow="The engine" title="Matching that thinks about time, need, distance and fairness" align="left" />
          <p className="mt-5 text-ink-400">
            Three interchangeable strategies (first-come-first-served, expiry-first and smart) run on the same Java engine.
            A built-in simulator replays hundreds of campus days to show, with numbers, which one rescues the most food.
          </p>
          <div className="mt-8 flex flex-wrap gap-3 text-sm">
            <span className="glass rounded-xl px-3 py-2 text-ink-300"><span className="font-semibold text-ink-100">PriorityQueue</span> scheduling</span>
            <span className="glass rounded-xl px-3 py-2 text-ink-300"><span className="font-semibold text-ink-100">Strategy</span> pattern</span>
            <span className="glass rounded-xl px-3 py-2 text-ink-300"><span className="font-semibold text-ink-100">State</span> machine dispatches</span>
          </div>
        </div>
        <div className="space-y-3">
          {factors.map((f, i) => (
            <FadeContent key={f.title} blur duration={0.8} delay={i * 0.1}>
              <div className="glass flex items-start gap-4 rounded-2xl p-4">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl" style={{ color: f.color, background: `${f.color}1c` }}>{f.icon}</span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold">{f.title}</h3>
                  </div>
                  <p className="mt-1 text-sm text-ink-400">{f.text}</p>
                  <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-white/6">
                    <motion.div
                      className="h-full rounded-full"
                      style={{ background: f.color }}
                      initial={{ width: 0 }}
                      whileInView={{ width: `${f.w}%` }}
                      viewport={{ once: true }}
                      transition={{ duration: 1.2, delay: 0.2 + i * 0.1, ease: [0.22, 1, 0.36, 1] }}
                    />
                  </div>
                </div>
              </div>
            </FadeContent>
          ))}
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ trust & values

function Trust() {
  const hashes = [
    'a3f9c1e07b4d2286f5e1c90d7ab35f12c8e4097bd61a2f3e5c7d9b0a1e4f6c82',
    '5d2e8b19c4a7f03e61b9d5c2a8e7f4b0d3c6a9e2f5b8d1c4a7e0f3b6d9c2e5a8',
    'e8b1d4c7a0f3e6b9d2c5a8f1e4b7d0c3a6f9e2b5d8c1a4f7e0b3d6c9a2f5e8b1',
  ];
  const values = [
    { icon: <Users className="size-5" />, name: 'Sah-astitva', meaning: 'Co-existence', text: 'Campus privilege turned into everyday support for the neighbourhood.' },
    { icon: <Sprout className="size-5" />, name: 'Samyak Vikas', meaning: 'Harmony with nature', text: 'Food eaten, not landfilled, measured in kg of CO₂e avoided.' },
    { icon: <UtensilsCrossed className="size-5" />, name: 'Sadupayog', meaning: 'Right use of resources', text: 'Food fulfils its purpose: nourishment.' },
    { icon: <Scale className="size-5" />, name: 'Nyaya', meaning: 'Distributive justice', text: 'Need-first, fairness-aware matching, with no money involved.' },
  ];
  return (
    <section id="trust" className="relative py-28">
      <div className="mx-auto max-w-6xl px-5">
        <div className="grid gap-10 lg:grid-cols-5">
          <div className="lg:col-span-2">
            <SectionTitle eyebrow="Trust" title="Every action, sealed in a tamper-evident chain" align="left" />
            <p className="mt-5 text-ink-400">
              Each log line carries an HMAC-SHA-256 of its content and the previous line, keyed with a secret only the server
              holds. Edit or delete a single row in the database and verification pinpoints it instantly.
            </p>
          </div>
          <div className="glass relative overflow-hidden rounded-3xl p-5 font-mono text-xs lg:col-span-3">
            {hashes.map((h, i) => (
              <div key={h} className="flex items-center gap-3 border-b hairline py-3 last:border-0">
                <Fingerprint className="size-4 shrink-0 text-leaf-400" />
                <span className="text-ink-500">#{1042 + i}</span>
                <DecryptedText text={h} animateOn="view" sequential revealDirection="start" speed={18} className="truncate text-ink-200" encryptedClassName="text-saffron-400/70" parentClassName="min-w-0 truncate" />
              </div>
            ))}
            <div className="mt-4 flex items-center gap-2 text-leaf-300">
              <ShieldCheck className="size-4" /> chain intact · verified on demand from the coordinator console
            </div>
          </div>
        </div>

        <div className="mt-20">
          <SectionTitle eyebrow="Universal Human Values" title="Engineered around values, not just features" />
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {values.map((v, i) => (
              <FadeContent key={v.name} blur duration={0.8} delay={i * 0.08}>
                <div className="h-full rounded-3xl border hairline bg-white/[0.02] p-5">
                  <span className="text-saffron-300">{v.icon}</span>
                  <h3 className="mt-4 text-lg font-semibold">{v.name}</h3>
                  <p className="text-xs uppercase tracking-[0.14em] text-ink-500">{v.meaning}</p>
                  <p className="mt-3 text-sm text-ink-400">{v.text}</p>
                </div>
              </FadeContent>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ CTA + footer

function FinalCta() {
  return (
    <section className="relative overflow-hidden py-32">
      <div className="absolute inset-0 opacity-70">
        <Aurora colorStops={['#f59e0b', '#10b981', '#fb7185']} amplitude={1.1} blend={0.55} speed={0.6} intro={false} />
      </div>
      <div className="absolute inset-0 bg-gradient-to-b from-night-900 via-night-900/40 to-night-900" />
      <div className="relative mx-auto max-w-3xl px-5 text-center">
        <Truck className="mx-auto size-8 text-saffron-300" />
        <h2 className="mt-6 font-display text-[clamp(2.2rem,5vw,3.8rem)] font-semibold leading-[1.02] tracking-tight">
          Tonight's leftovers can be<br />
          <span className="text-gradient">someone's dinner.</span>
        </h2>
        <p className="mx-auto mt-5 max-w-xl text-ink-300">
          Sign up as a kitchen, a shelter or a volunteer. Organisations are verified by the campus coordinator before going live.
        </p>
        <div className="mt-9 flex flex-wrap justify-center gap-3">
          <Link to="/register"><Button size="lg" trailing={<ArrowRight className="size-4" />}>Create your account</Button></Link>
          <Link to="/login"><Button size="lg" variant="secondary">I already have one</Button></Link>
        </div>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer className="border-t hairline py-10">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-5 text-sm text-ink-500 sm:flex-row">
        <Logo />
        <p>Pure Java 17 backend · React + Three.js frontend · Built for the OOP project exhibition</p>
      </div>
    </footer>
  );
}

function SectionTitle({ eyebrow, title, align = 'center' }: { eyebrow: string; title: string; align?: 'center' | 'left' }) {
  return (
    <div className={align === 'center' ? 'mx-auto max-w-2xl text-center' : ''}>
      <p className="text-xs font-semibold uppercase tracking-[0.22em] text-saffron-300">{eyebrow}</p>
      <h2 className="mt-3 font-display text-[clamp(1.9rem,4vw,3rem)] font-semibold leading-[1.05] tracking-tight">{title}</h2>
    </div>
  );
}
