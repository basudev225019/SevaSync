import { 
  Sparkles, 
  Send, 
  Leaf, 
  Clock, 
  CheckCircle2, 
  Plus, 
  Cpu, 
  ChevronRight,
  TrendingUp,
  Heart,
  HeartHandshake
} from 'lucide-react';
import { StatCard } from '../components/StatCard';
import { Dispatch, FoodBatch, ImpactReport } from '../types';

interface DashboardProps {
  report: ImpactReport | null;
  batches: FoodBatch[];
  dispatches: Dispatch[];
  onOpenCreateBatch: () => void;
  onOpenCreateRecipient: () => void;
  onRunMatching: () => void;
  onNavigateTab: (tab: string) => void;
}

export const Dashboard: React.FC<DashboardProps> = ({
  report,
  batches,
  dispatches,
  onOpenCreateBatch,
  onOpenCreateRecipient,
  onRunMatching,
  onNavigateTab,
}) => {
  const availableBatches = batches.filter((b) => b.status === 'AVAILABLE');
  const activeDispatches = dispatches.filter((d) => d.status === 'ASSIGNED');

  // Format time remaining
  const getTimeRemaining = (expiresAt: string) => {
    const diff = new Date(expiresAt).getTime() - new Date().getTime();
    if (diff <= 0) return { text: 'Expired', urgent: true, ratio: 0 };
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const mins = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const urgent = hours < 2;
    return {
      text: hours > 0 ? `${hours}h ${mins}m left` : `${mins}m left`,
      urgent,
      hours,
    };
  };

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Hero Welcome Banner */}
      <div
        className="glass-panel"
        style={{
          padding: '2rem',
          background: 'radial-gradient(ellipse at top left, rgba(16, 185, 129, 0.15) 0%, rgba(15, 23, 42, 0.8) 70%)',
          border: '1px solid rgba(16, 185, 129, 0.25)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1.5rem',
        }}
      >
        <div style={{ maxWidth: '680px' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <span className="badge badge-emerald">
              <Sparkles size={12} />
              Zero Campus Food Waste Initiative
            </span>
            <span className="badge badge-indigo">
              Real-time SQLite Storage
            </span>
          </div>
          <h1 style={{ fontSize: '2.25rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.03em', lineHeight: 1.2, marginBottom: '0.75rem' }}>
            Smart Redistribution & Freshness Engine
          </h1>
          <p style={{ fontSize: '0.9375rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            Intelligently pairs soonest-expiring hostel mess surplus with high-need campus beneficiaries. Powered by double priority-queues and cryptographic audit trails.
          </p>
        </div>

        {/* Quick Action Buttons */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button onClick={onOpenCreateBatch} className="btn btn-primary">
            <Plus size={16} />
            Log Surplus Food
          </button>
          <button onClick={onOpenCreateRecipient} className="btn btn-secondary">
            <HeartHandshake size={16} />
            Register Beneficiary
          </button>
          <button onClick={onRunMatching} className="btn btn-accent">
            <Cpu size={16} />
            Run Smart Matching
          </button>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: '1.25rem',
      }}>
        <StatCard
          title="Servings Donated"
          value={report?.servingsDonated || 0}
          subtitle="Total surplus meals cataloged"
          icon={Sparkles}
          variant="emerald"
          trend="+100% campus tracked"
        />
        <StatCard
          title="Servings Delivered"
          value={report?.servingsDelivered || 0}
          subtitle={`${report?.deliveryRatePercent || 0}% delivery success rate`}
          icon={Heart}
          variant="cyan"
          trend="Delivered to beneficiaries"
        />
        <StatCard
          title="Active Dispatches"
          value={activeDispatches.length}
          subtitle={`${activeDispatches.reduce((acc, d) => acc + d.servings, 0)} servings in transit`}
          icon={Send}
          variant="amber"
          trend="Awaiting volunteer delivery"
        />
        <StatCard
          title="Food Saved"
          value={`${report?.foodSavedKg || 0} kg`}
          subtitle="Direct waste diverted"
          icon={Leaf}
          variant="emerald"
        />
        <StatCard
          title="CO₂e Avoided"
          value={`${report?.co2AvoidedKg || 0} kg`}
          subtitle="Greenhouse gas emissions prevented"
          icon={TrendingUp}
          variant="indigo"
        />
      </div>

      {/* Two Column Section: Expiry Watchlist & In-Transit Dispatches */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '1.5rem' }}>
        
        {/* Urgent Freshness Watchlist */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Clock size={18} color="var(--amber)" />
              <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>Freshness Expiry Watchlist</h3>
            </div>
            <button
              onClick={() => onNavigateTab('batches')}
              className="btn btn-secondary btn-sm"
              style={{ fontSize: '0.75rem' }}
            >
              View All Batches <ChevronRight size={14} />
            </button>
          </div>

          {availableBatches.length === 0 ? (
            <div style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '2.5rem',
              color: 'var(--text-muted)',
              textAlign: 'center',
            }}>
              <CheckCircle2 size={36} color="var(--emerald)" style={{ marginBottom: '0.75rem', opacity: 0.8 }} />
              <p style={{ fontWeight: 600, color: '#ffffff' }}>No active surplus batches</p>
              <p style={{ fontSize: '0.8125rem', marginTop: '0.25rem' }}>All food has been allocated or delivered!</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {availableBatches.slice(0, 4).map((batch) => {
                const remaining = getTimeRemaining(batch.expiresAt);
                return (
                  <div
                    key={batch.id}
                    style={{
                      background: 'rgba(15, 23, 42, 0.6)',
                      border: `1px solid ${remaining.urgent ? 'rgba(245, 158, 11, 0.3)' : 'var(--border-subtle)'}`,
                      borderRadius: 'var(--radius-md)',
                      padding: '0.875rem 1rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '1rem',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                        <span style={{ fontWeight: 700, color: '#ffffff', fontSize: '0.9375rem' }}>
                          {batch.foodName}
                        </span>
                        {batch.vegetarian && (
                          <span className="badge badge-emerald" style={{ fontSize: '0.65rem' }}>Veg</span>
                        )}
                        <span className="badge badge-slate" style={{ fontSize: '0.65rem' }}>{batch.category}</span>
                      </div>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        Donor: {batch.donorId} • {batch.remainingServings} of {batch.servings} servings left
                      </p>
                    </div>

                    <div style={{ textAlign: 'right' }}>
                      <span className={`badge ${remaining.urgent ? 'badge-amber' : 'badge-emerald'}`}>
                        <Clock size={12} />
                        {remaining.text}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Live Active Dispatches */}
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Send size={18} color="var(--indigo)" />
              <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>Active In-Transit Dispatches</h3>
            </div>
            <button
              onClick={() => onNavigateTab('dispatches')}
              className="btn btn-secondary btn-sm"
              style={{ fontSize: '0.75rem' }}
            >
              Manage Dispatches <ChevronRight size={14} />
            </button>
          </div>

          {activeDispatches.length === 0 ? (
            <div style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '2.5rem',
              color: 'var(--text-muted)',
              textAlign: 'center',
            }}>
              <CheckCircle2 size={36} color="var(--indigo)" style={{ marginBottom: '0.75rem', opacity: 0.8 }} />
              <p style={{ fontWeight: 600, color: '#ffffff' }}>No pending dispatches</p>
              <p style={{ fontSize: '0.8125rem', marginTop: '0.25rem' }}>Run Smart Matching to generate new volunteer dispatches.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {activeDispatches.slice(0, 4).map((d) => (
                <div
                  key={d.id}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid rgba(99, 102, 241, 0.25)',
                    borderRadius: 'var(--radius-md)',
                    padding: '0.875rem 1rem',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                      <span style={{ fontWeight: 700, color: '#ffffff', fontSize: '0.875rem' }}>{d.id}</span>
                      <span className="badge badge-indigo">ASSIGNED</span>
                    </div>
                    <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                      Batch: {d.batchId} → Recipient: {d.recipientId}
                    </p>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <span style={{ fontSize: '1rem', fontWeight: 800, color: 'var(--emerald)' }}>
                      {d.servings}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.25rem' }}>
                      servings
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  );
};
