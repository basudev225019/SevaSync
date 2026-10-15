import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '../../services/api';
import { needLabel, recipientTypeLabel } from '../../lib/format';
import type { LatLng, NeedLevel, RecipientType } from '../../types';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input, Segmented, Select } from '../ui/Field';
import { errorMessage } from '../../lib/errors';
import { MapPicker } from '../MapPicker';

/** Coordinator onboards a recipient organisation after a field visit (already verified). */
export function CreateRecipientModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [recipientType, setRecipientType] = useState<RecipientType>('SHELTER');
  const [needLevel, setNeedLevel] = useState<NeedLevel>('HIGH');
  const [capacity, setCapacity] = useState('60');
  const [location, setLocation] = useState<LatLng | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api.createUser({
        role: 'RECIPIENT', name, email, phone, password, recipientType, needLevel,
        dailyCapacity: Number(capacity), lat: location?.lat, lng: location?.lng,
      }),
    onSuccess: (u) => {
      toast.success(`${u.name} added as a verified recipient`);
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['network'] });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add a recipient organisation"
      subtitle="Verified immediately and included in the next matching run."
      width="lg"
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={create.isPending} onClick={() => create.mutate()}>Create recipient</Button></>}
    >
      <div className="grid gap-5 md:grid-cols-2">
        <div className="space-y-3.5">
          <Input label="Organisation name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Railway Night Shelter" />
          <div className="grid grid-cols-2 gap-3">
            <Select label="Type" value={recipientType} onChange={(e) => setRecipientType(e.target.value as RecipientType)}>
              {(Object.keys(recipientTypeLabel) as RecipientType[]).map((t) => <option key={t} value={t}>{recipientTypeLabel[t]}</option>)}
            </Select>
            <Input label="Meals per day" type="number" min={1} max={5000} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
          </div>
          <Segmented label="Need level" value={needLevel} onChange={setNeedLevel} options={(['NORMAL', 'HIGH', 'CRITICAL'] as NeedLevel[]).map((n) => ({ value: n, label: needLabel[n] }))} />
          <Input label="Contact e-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input label="Mobile" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <Input label="Temporary password" type="text" value={password} onChange={(e) => setPassword(e.target.value)} hint="8+ characters with a letter and a digit" />
        </div>
        <MapPicker value={location} onChange={setLocation} color="#fb7185" />
      </div>
    </Modal>
  );
}
