import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '../../services/api';
import { donorTypeLabel } from '../../lib/format';
import type { DonorType, LatLng } from '../../types';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input, Select } from '../ui/Field';
import { errorMessage } from '../../lib/errors';
import { MapPicker } from '../MapPicker';

/** Coordinator onboards a kitchen directly (already verified). */
export function CreateDonorModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [donorType, setDonorType] = useState<DonorType>('CANTEEN');
  const [location, setLocation] = useState<LatLng | null>(null);

  const create = useMutation({
    mutationFn: () => api.createUser({ role: 'DONOR', name, email, phone, password, donorType, lat: location?.lat, lng: location?.lng }),
    onSuccess: (u) => {
      toast.success(`${u.name} added as a verified kitchen`);
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
      title="Add a kitchen"
      subtitle="Created accounts are verified immediately. Share the temporary password with the kitchen manager."
      width="lg"
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={create.isPending} onClick={() => create.mutate()}>Create kitchen</Button></>}
    >
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
        <div className="space-y-3.5">
          <Input label="Kitchen name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Central Canteen" />
          <Select label="Kind" value={donorType} onChange={(e) => setDonorType(e.target.value as DonorType)}>
            {(Object.keys(donorTypeLabel) as DonorType[]).map((t) => <option key={t} value={t}>{donorTypeLabel[t]}</option>)}
          </Select>
          <Input label="Manager e-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input label="Mobile" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <Input label="Temporary password" type="text" value={password} onChange={(e) => setPassword(e.target.value)} hint="8+ characters with a letter and a digit" />
        </div>
        <MapPicker value={location} onChange={setLocation} />
      </div>
    </Modal>
  );
}
