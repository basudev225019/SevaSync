import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Truck } from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Segmented } from '../components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { DispatchList } from '../components/DispatchList';
import { roleColor } from '../lib/theme';
import type { DispatchStatus, Role } from '../types';

type Filter = 'ACTIVE' | 'DELIVERED' | 'CANCELLED' | 'ALL';

const COPY: Record<Role, { eyebrow: string; title: string; description: string }> = {
  RECIPIENT: { eyebrow: 'Deliveries', title: 'Food coming your way', description: 'Track every delivery live and confirm it when it arrives.' },
  DONOR: { eyebrow: 'Deliveries', title: 'Where your food went', description: 'Every hand-over of your food, with the volunteer and the recipient\'s rating.' },
  ADMIN: { eyebrow: 'Dispatches', title: 'Every hand-over on campus', description: 'Cancel a dispatch if a recipient closes; untouched food returns to the pool and is re-matched.' },
  VOLUNTEER: { eyebrow: 'Runs', title: 'Your runs', description: '' },
};

const ACTIVE: DispatchStatus[] = ['ASSIGNED', 'CLAIMED', 'PICKED_UP'];

export default function DispatchesView() {
  const { user } = useAuth();
  const [filter, setFilter] = useState<Filter>('ACTIVE');
  const list = useQuery({ queryKey: ['dispatches', user?.role === 'ADMIN' ? 'all' : 'mine'], queryFn: () => api.dispatches() });
  if (!user) return null;
  const copy = COPY[user.role];
  const items = (list.data ?? []).filter((d) =>
    filter === 'ALL' ? true : filter === 'ACTIVE' ? ACTIVE.includes(d.status) || (d.status === 'DELIVERED' && !d.confirmedAt && user.role === 'RECIPIENT') : d.status === filter,
  );

  return (
    <>
      <PageHeader eyebrow={copy.eyebrow} accent={roleColor[user.role]} title={copy.title} description={copy.description} />
      <Segmented
        className="mb-6 max-w-xl"
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'ACTIVE', label: 'In progress' },
          { value: 'DELIVERED', label: 'Delivered' },
          { value: 'CANCELLED', label: 'Cancelled' },
          { value: 'ALL', label: 'All' },
        ]}
      />
      {list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> : list.isLoading ? (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">{[0, 1].map((i) => <Skeleton key={i} className="h-56" />)}</div>
      ) : items.length === 0 ? (
        <EmptyState icon={<Truck className="size-5" />} title="Nothing to show">No dispatches match this filter yet.</EmptyState>
      ) : (
        <DispatchList items={items} columns={user.role === 'ADMIN' ? 3 : 2} />
      )}
    </>
  );
}
