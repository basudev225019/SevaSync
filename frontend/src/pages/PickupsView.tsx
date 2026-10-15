import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bike, PartyPopper } from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Segmented } from '../components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/Feedback';
import { DispatchList } from '../components/DispatchList';
import { vehicleLabel } from '../lib/format';
import { roleColor } from '../lib/theme';

type Tab = 'open' | 'mine' | 'done';

export default function PickupsView() {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>('open');
  const open = useQuery({ queryKey: ['pickups'], queryFn: api.openPickups, refetchInterval: 30_000 });
  const mine = useQuery({ queryKey: ['dispatches', 'mine'], queryFn: () => api.dispatches() });
  const active = (mine.data ?? []).filter((d) => d.status === 'CLAIMED' || d.status === 'PICKED_UP');
  const done = (mine.data ?? []).filter((d) => d.status === 'DELIVERED');

  const query = tab === 'open' ? open : mine;
  const items = tab === 'open' ? open.data ?? [] : tab === 'mine' ? active : done;

  return (
    <>
      <PageHeader
        eyebrow="Pickups"
        accent={roleColor.VOLUNTEER}
        title="Food waiting for you"
        description={`Nearest first. Anything too big for your ${user?.vehicle ? vehicleLabel[user.vehicle].toLowerCase() : 'vehicle'} goes to the bottom. You can hold up to 3 pickups at a time.`}
      />
      <Segmented
        className="mb-6 max-w-lg"
        value={tab}
        onChange={setTab}
        options={[
          { value: 'open', label: `Open (${open.data?.length ?? 0})`, color: roleColor.VOLUNTEER },
          { value: 'mine', label: `My runs (${active.length})`, color: roleColor.VOLUNTEER },
          { value: 'done', label: `Delivered (${done.length})`, color: '#34d399' },
        ]}
      />
      {query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} /> : query.isLoading ? (
        <div className="grid gap-4 lg:grid-cols-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-56" />)}</div>
      ) : items.length === 0 ? (
        tab === 'open' ? (
          <EmptyState icon={<PartyPopper className="size-5" />} title="No food is waiting">Every batch already has a volunteer. Check back after dinner service.</EmptyState>
        ) : (
          <EmptyState icon={<Bike className="size-5" />} title={tab === 'mine' ? 'No active runs' : 'No deliveries yet'}>
            {tab === 'mine' ? 'Claim an open pickup to start.' : 'Your completed deliveries will be listed here.'}
          </EmptyState>
        )
      ) : (
        <DispatchList items={items} />
      )}
    </>
  );
}
