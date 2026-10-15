import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AnimatePresence, motion } from 'motion/react';
import { Ban, CheckCircle2, HandHeart, Pencil, RotateCcw, UtensilsCrossed, Users } from 'lucide-react';
import { api } from '../services/api';
import { PageHeader } from '../components/PageHeader';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { Input, Segmented } from '../components/ui/Field';
import { Modal } from '../components/ui/Modal';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { errorMessage } from '../lib/errors';
import { NeedPill } from '../components/ui/StatusPill';
import { CreateDonorModal } from '../components/modals/CreateDonorModal';
import { CreateRecipientModal } from '../components/modals/CreateRecipientModal';
import { donorTypeLabel, needLabel, recipientTypeLabel, roleLabel, timeAgo, vehicleLabel } from '../lib/format';
import { roleColor } from '../lib/theme';
import type { AccountStatus, NeedLevel, Role, User } from '../types';

type Tab = 'PENDING' | Exclude<Role, 'ADMIN'>;

export default function PartnersView() {
  const [tab, setTab] = useState<Tab>('PENDING');
  const [adding, setAdding] = useState<'donor' | 'recipient' | null>(null);
  const [editing, setEditing] = useState<User | null>(null);
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.users() });
  const all = (users.data ?? []).filter((u) => u.role !== 'ADMIN');
  const pending = all.filter((u) => u.status === 'PENDING');
  const items = tab === 'PENDING' ? pending : all.filter((u) => u.role === tab);

  return (
    <>
      <PageHeader
        eyebrow="Partners"
        accent={roleColor.ADMIN}
        title="Kitchens, shelters and volunteers"
        description="Verify organisations before they go live, adjust need levels after field visits, and suspend accounts if something goes wrong."
        actions={
          <>
            <Button variant="secondary" icon={<UtensilsCrossed className="size-4" />} onClick={() => setAdding('donor')}>Add kitchen</Button>
            <Button icon={<HandHeart className="size-4" />} onClick={() => setAdding('recipient')}>Add recipient</Button>
          </>
        }
      />
      <Segmented
        className="mb-6 max-w-2xl"
        value={tab}
        onChange={setTab}
        options={[
          { value: 'PENDING', label: `Awaiting verification (${pending.length})`, color: '#fbbf24' },
          { value: 'DONOR', label: 'Kitchens', color: roleColor.DONOR },
          { value: 'RECIPIENT', label: 'Recipients', color: roleColor.RECIPIENT },
          { value: 'VOLUNTEER', label: 'Volunteers', color: roleColor.VOLUNTEER },
        ]}
      />
      {users.isError ? <ErrorState error={users.error} onRetry={() => users.refetch()} /> : users.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-36" />)}</div>
      ) : items.length === 0 ? (
        <EmptyState icon={<Users className="size-5" />} title={tab === 'PENDING' ? 'Nobody is waiting' : 'No partners yet'}>
          {tab === 'PENDING' ? 'New kitchens and shelters that sign up will appear here for verification.' : 'Use the buttons above to add one.'}
        </EmptyState>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <AnimatePresence initial={false}>
            {items.map((u) => <PartnerCard key={u.id} u={u} onEdit={() => setEditing(u)} />)}
          </AnimatePresence>
        </div>
      )}
      <CreateDonorModal open={adding === 'donor'} onClose={() => setAdding(null)} />
      <CreateRecipientModal open={adding === 'recipient'} onClose={() => setAdding(null)} />
      <EditRecipient user={editing} onClose={() => setEditing(null)} />
    </>
  );
}

const STATUS_COLOR: Record<AccountStatus, string> = { ACTIVE: '#34d399', PENDING: '#fbbf24', SUSPENDED: '#f43f5e' };

function PartnerCard({ u, onEdit }: { u: User; onEdit: () => void }) {
  const qc = useQueryClient();
  const setStatus = useMutation({
    mutationFn: (status: AccountStatus) => api.setUserStatus(u.id, status),
    onSuccess: (x) => {
      toast.success(`${x.name} is now ${x.status.toLowerCase()}`);
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['network'] });
      qc.invalidateQueries({ queryKey: ['impact'] });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const detail =
    u.role === 'DONOR' && u.donorType ? donorTypeLabel[u.donorType]
      : u.role === 'RECIPIENT' && u.recipientType ? `${recipientTypeLabel[u.recipientType]} · ${u.dailyCapacity} meals/day`
        : u.role === 'VOLUNTEER' && u.vehicle ? vehicleLabel[u.vehicle] : roleLabel[u.role];

  return (
    <motion.div layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
      <Card className="h-full">
        <div className="flex items-start gap-3">
          <Avatar name={u.name} role={u.role} size={42} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center justify-between gap-2">
              <h3 className="truncate font-semibold">{u.name}</h3>
              <Badge color={STATUS_COLOR[u.status]} dot>{u.status.toLowerCase()}</Badge>
            </div>
            <p className="truncate text-xs text-ink-400">{detail}</p>
            <p className="mt-1 truncate text-xs text-ink-500">{u.email} · {u.phone} · joined {timeAgo(u.createdAt)}</p>
            {u.role === 'RECIPIENT' && u.needLevel && <div className="mt-2"><NeedPill level={u.needLevel} /></div>}
          </div>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {u.status === 'PENDING' && (
            <>
              <Button size="sm" variant="success" icon={<CheckCircle2 className="size-3.5" />} loading={setStatus.isPending} onClick={() => setStatus.mutate('ACTIVE')}>Verify</Button>
              <Button size="sm" variant="danger" icon={<Ban className="size-3.5" />} disabled={setStatus.isPending} onClick={() => setStatus.mutate('SUSPENDED')}>Reject</Button>
            </>
          )}
          {u.status === 'ACTIVE' && (
            <Button size="sm" variant="ghost" icon={<Ban className="size-3.5" />} loading={setStatus.isPending} onClick={() => setStatus.mutate('SUSPENDED')}>Suspend</Button>
          )}
          {u.status === 'SUSPENDED' && (
            <Button size="sm" variant="secondary" icon={<RotateCcw className="size-3.5" />} loading={setStatus.isPending} onClick={() => setStatus.mutate('ACTIVE')}>Reactivate</Button>
          )}
          {u.role === 'RECIPIENT' && (
            <Button size="sm" variant="ghost" icon={<Pencil className="size-3.5" />} onClick={onEdit}>Need & capacity</Button>
          )}
        </div>
      </Card>
    </motion.div>
  );
}

function EditRecipient({ user, onClose }: { user: User | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [need, setNeed] = useState<NeedLevel>('NORMAL');
  const [capacity, setCapacity] = useState('');
  const [shownFor, setShownFor] = useState<string | null>(null);
  if (user && shownFor !== user.id) {
    setShownFor(user.id);
    setNeed(user.needLevel ?? 'NORMAL');
    setCapacity(String(user.dailyCapacity ?? ''));
  }
  const save = useMutation({
    mutationFn: () => api.updateUser(user!.id, { needLevel: need, dailyCapacity: Number(capacity) }),
    onSuccess: () => {
      toast.success('Recipient updated; matching uses the new values from now on');
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['network'] });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open={!!user}
      onClose={onClose}
      title={user ? `Adjust ${user.name}` : ''}
      subtitle="Need level directly changes matching priority. Record why on the audit trail by keeping notes of the visit."
      width="sm"
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}
    >
      <Segmented label="Need level" value={need} onChange={setNeed} options={(['NORMAL', 'HIGH', 'CRITICAL'] as NeedLevel[]).map((n) => ({ value: n, label: needLabel[n] }))} />
      <Input className="mt-4" label="Meals per day" type="number" min={1} max={5000} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
    </Modal>
  );
}
