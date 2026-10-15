import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { KeyRound, Save } from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input, Segmented, Select } from '../components/ui/Field';
import { errorMessage } from '../lib/errors';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { MapPicker } from '../components/MapPicker';
import { donorTypeLabel, needLabel, roleLabel, vehicleLabel } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { DonorType, LatLng, NeedLevel, VehicleType } from '../types';

export default function ProfileView() {
  const { user, refresh } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [address, setAddress] = useState(user?.address ?? '');
  const [location, setLocation] = useState<LatLng | null>(user?.location ?? null);
  const [donorType, setDonorType] = useState<DonorType | undefined>(user?.donorType);
  const [vehicle, setVehicle] = useState<VehicleType | undefined>(user?.vehicle);
  const [need, setNeed] = useState<NeedLevel | undefined>(user?.needLevel);
  const [capacity, setCapacity] = useState(String(user?.dailyCapacity ?? ''));
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');

  const save = useMutation({
    mutationFn: () =>
      api.updateProfile({
        name, phone, address, lat: location?.lat, lng: location?.lng,
        ...(user?.role === 'DONOR' && { donorType }),
        ...(user?.role === 'VOLUNTEER' && { vehicle }),
        ...(user?.role === 'RECIPIENT' && { needLevel: need, dailyCapacity: Number(capacity) }),
      }),
    onSuccess: async () => {
      await refresh();
      toast.success('Profile saved');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const password = useMutation({
    mutationFn: () => api.changePassword(current, next),
    onSuccess: (r) => {
      toast.success(r.message);
      setCurrent('');
      setNext('');
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (!user) return null;
  const located = user.role !== 'ADMIN';

  return (
    <>
      <PageHeader eyebrow="Profile" accent={roleColor[user.role]} title="Your account" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <div className="mb-6 flex items-center gap-4">
            <Avatar name={user.name} role={user.role} size={56} />
            <div>
              <p className="text-lg font-semibold">{user.name}</p>
              <div className="mt-1 flex flex-wrap gap-2">
                <Badge color={roleColor[user.role]}>{roleLabel[user.role]}</Badge>
                <Badge color={user.status === 'ACTIVE' ? '#1f9d55' : '#e8862a'} dot>{user.status.toLowerCase()}</Badge>
                <span className="text-xs text-ink-500">{user.email} · {user.id}</span>
              </div>
            </div>
          </div>
          <form
            className="grid grid-cols-1 gap-4 sm:grid-cols-2"
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
            <Input label="Mobile" value={phone} onChange={(e) => setPhone(e.target.value)} />
            <Input className="sm:col-span-2" label="Address" value={address} onChange={(e) => setAddress(e.target.value)} maxLength={160} />
            {user.role === 'DONOR' && (
              <Select label="Kind of kitchen" value={donorType} onChange={(e) => setDonorType(e.target.value as DonorType)}>
                {(Object.keys(donorTypeLabel) as DonorType[]).map((t) => <option key={t} value={t}>{donorTypeLabel[t]}</option>)}
              </Select>
            )}
            {user.role === 'VOLUNTEER' && (
              <Segmented className="sm:col-span-2" label="Vehicle" value={vehicle ?? 'BICYCLE'} onChange={setVehicle} options={(['WALK', 'BICYCLE', 'SCOOTER', 'CAR'] as VehicleType[]).map((v) => ({ value: v, label: vehicleLabel[v], color: roleColor.VOLUNTEER }))} />
            )}
            {user.role === 'RECIPIENT' && (
              <>
                <Input label="Meals per day" type="number" min={1} max={5000} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
                <Segmented label="Need level" value={need ?? 'NORMAL'} onChange={setNeed} options={(['NORMAL', 'HIGH', 'CRITICAL'] as NeedLevel[]).map((n) => ({ value: n, label: needLabel[n] }))} />
                <p className="text-xs text-ink-500 sm:col-span-2">You can lower your need level yourself; raising it needs the coordinator.</p>
              </>
            )}
            {located && (
              <div className="sm:col-span-2">
                <span className="label">Location</span>
                <MapPicker value={location} onChange={setLocation} color={roleColor[user.role]} />
              </div>
            )}
            <div className="sm:col-span-2">
              <Button type="submit" icon={<Save className="size-4" />} loading={save.isPending}>Save profile</Button>
            </div>
          </form>
        </Card>

        <Card className="h-fit lg:col-span-2">
          <CardHeader title="Change password" subtitle="Signs you out on every other device" icon={<KeyRound className="size-4" />} />
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              password.mutate();
            }}
          >
            <Input label="Current password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
            <Input label="New password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} hint="8+ characters with a letter and a digit" />
            <Button type="submit" variant="secondary" loading={password.isPending} disabled={!current || next.length < 8}>Update password</Button>
          </form>
        </Card>
      </div>
    </>
  );
}
