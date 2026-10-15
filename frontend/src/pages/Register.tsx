import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Bike, HandHeart, UtensilsCrossed } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../context/AuthContext';
import Stepper, { Step } from '../components/reactbits/Stepper';
import { Input, Segmented, Select } from '../components/ui/Field';
import { errorMessage } from '../lib/errors';
import { MapPicker } from '../components/MapPicker';
import { AuthLayout } from './Login';
import { donorTypeLabel, needLabel, recipientTypeLabel, vehicleLabel } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { DonorType, LatLng, NeedLevel, RecipientType, RegistrationForm, VehicleType } from '../types';

type SignUpRole = RegistrationForm['role'];

const ROLES: { id: SignUpRole; title: string; text: string; icon: React.ReactNode; note: string }[] = [
  { id: 'DONOR', title: 'Kitchen or event team', text: 'I have surplus food to give away.', icon: <UtensilsCrossed className="size-5" />, note: 'Verified by the coordinator before going live' },
  { id: 'VOLUNTEER', title: 'Student volunteer', text: 'I can carry food from kitchens to shelters.', icon: <Bike className="size-5" />, note: 'Active immediately' },
  { id: 'RECIPIENT', title: 'Shelter or NGO', text: 'We serve people who need meals.', icon: <HandHeart className="size-5" />, note: 'Verified by the coordinator before going live' },
];

const emailOk = (s: string) => /^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(s.trim());
const phoneOk = (s: string) => /^(\+91|91|0)?[6-9]\d{9}$/.test(s.replace(/[\s-]/g, ''));
const passwordOk = (s: string) => s.length >= 8 && /[a-z]/i.test(s) && /\d/.test(s);

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [role, setRole] = useState<SignUpRole>('VOLUNTEER');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [donorType, setDonorType] = useState<DonorType>('HOSTEL_MESS');
  const [recipientType, setRecipientType] = useState<RecipientType>('SHELTER');
  const [needLevel, setNeedLevel] = useState<NeedLevel>('NORMAL');
  const [capacity, setCapacity] = useState('60');
  const [vehicle, setVehicle] = useState<VehicleType>('BICYCLE');
  const [location, setLocation] = useState<LatLng | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const valid = useMemo(() => {
    switch (step) {
      case 1: return true;
      case 2: return name.trim().length > 1 && emailOk(email) && passwordOk(password) && phoneOk(phone);
      case 3: return role !== 'RECIPIENT' || (Number(capacity) >= 1 && Number(capacity) <= 5000);
      case 4: return location !== null && !busy;
      default: return true;
    }
  }, [step, name, email, password, phone, role, capacity, location, busy]);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const form: RegistrationForm = {
        role, name: name.trim(), email: email.trim(), password, phone, address: address.trim(),
        lat: location?.lat, lng: location?.lng,
        ...(role === 'DONOR' && { donorType }),
        ...(role === 'RECIPIENT' && { recipientType, needLevel, dailyCapacity: Number(capacity) }),
        ...(role === 'VOLUNTEER' && { vehicle }),
      };
      await register(form);
      navigate('/app', { replace: true });
    } catch (e) {
      setError(errorMessage(e));
      setStep(2); // most server-side errors (e-mail taken, phone format) live on the account step
    } finally {
      setBusy(false);
    }
  };

  const accent = roleColor[role];

  return (
    <AuthLayout
      wide
      title="Join SevaSync"
      subtitle={<>Already registered? <Link to="/login" className="font-semibold text-saffron-300 hover:underline">Sign in</Link></>}
    >
      {error && <p className="mb-4 rounded-xl border border-rose-400/20 bg-rose-500/10 px-3 py-2 text-sm text-rose-200" role="alert">{error}</p>}
      <Stepper
        step={step}
        onStepChange={setStep}
        onFinalStepCompleted={submit}
        linear
        width="100%"
        accentColor={accent}
        nextButtonProps={{ disabled: !valid }}
        completeButtonText={busy ? 'Creating…' : 'Create account'}
        nextButtonText="Continue"
      >
        <Step title="Role">
          <p className="mb-4 text-sm text-ink-400">How will you take part?</p>
          <div className="grid gap-2.5">
            {ROLES.map((r) => {
              const active = r.id === role;
              return (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => setRole(r.id)}
                  aria-pressed={active}
                  className={clsx('flex items-center gap-4 rounded-2xl border p-4 text-left transition', active ? 'bg-white/[0.06]' : 'border-white/8 hover:bg-white/[0.03]')}
                  style={active ? { borderColor: `${roleColor[r.id]}66`, boxShadow: `0 0 0 4px ${roleColor[r.id]}14` } : undefined}
                >
                  <span className="grid size-11 shrink-0 place-items-center rounded-xl" style={{ color: roleColor[r.id], background: `${roleColor[r.id]}1c` }}>{r.icon}</span>
                  <span className="min-w-0">
                    <span className="block font-semibold text-ink-100">{r.title}</span>
                    <span className="block text-sm text-ink-400">{r.text}</span>
                    <span className="mt-1 block text-[11px]" style={{ color: roleColor[r.id] }}>{r.note}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </Step>

        <Step title="Account">
          <div className="grid gap-3.5 sm:grid-cols-2">
            <Input className="sm:col-span-2" label={role === 'VOLUNTEER' ? 'Full name' : 'Organisation name'} value={name} onChange={(e) => setName(e.target.value)} placeholder={role === 'VOLUNTEER' ? 'Priya Nair' : 'Asha Bhavan Orphanage'} maxLength={80} />
            <Input label="E-mail" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={email && !emailOk(email) ? 'Enter a valid e-mail' : null} />
            <Input label="Mobile" type="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="98765 43210" error={phone && !phoneOk(phone) ? '10-digit Indian mobile number' : null} />
            <Input className="sm:col-span-2" label="Password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} hint="At least 8 characters with a letter and a digit" error={password && !passwordOk(password) ? 'At least 8 characters with a letter and a digit' : null} />
          </div>
        </Step>

        <Step title="Details">
          {role === 'DONOR' && (
            <Select label="Kind of kitchen" value={donorType} onChange={(e) => setDonorType(e.target.value as DonorType)}>
              {(Object.keys(donorTypeLabel) as DonorType[]).map((t) => <option key={t} value={t}>{donorTypeLabel[t]}</option>)}
            </Select>
          )}
          {role === 'RECIPIENT' && (
            <div className="grid gap-3.5 sm:grid-cols-2">
              <Select label="Organisation type" value={recipientType} onChange={(e) => setRecipientType(e.target.value as RecipientType)}>
                {(Object.keys(recipientTypeLabel) as RecipientType[]).map((t) => <option key={t} value={t}>{recipientTypeLabel[t]}</option>)}
              </Select>
              <Input label="Meals you can take per day" type="number" min={1} max={5000} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
              <Segmented
                className="sm:col-span-2"
                label="How urgent is your need?"
                value={needLevel}
                onChange={setNeedLevel}
                options={(['NORMAL', 'HIGH', 'CRITICAL'] as NeedLevel[]).map((n) => ({ value: n, label: needLabel[n] }))}
              />
              <p className="text-xs text-ink-500 sm:col-span-2">The coordinator confirms your need level during verification.</p>
            </div>
          )}
          {role === 'VOLUNTEER' && (
            <Segmented
              label="How do you get around?"
              value={vehicle}
              onChange={setVehicle}
              options={(['WALK', 'BICYCLE', 'SCOOTER', 'CAR'] as VehicleType[]).map((v) => ({ value: v, label: vehicleLabel[v], color: roleColor.VOLUNTEER }))}
            />
          )}
          <Input className="mt-4" label="Address (optional)" value={address} onChange={(e) => setAddress(e.target.value)} placeholder={role === 'VOLUNTEER' ? 'Hostel B, room 214' : 'Street and area'} maxLength={160} />
        </Step>

        <Step title="Location">
          <MapPicker value={location} onChange={setLocation} color={accent} />
        </Step>
      </Stepper>
    </AuthLayout>
  );
}
