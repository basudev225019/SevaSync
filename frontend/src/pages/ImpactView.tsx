import React from 'react';
import { 
  Leaf, 
  TrendingUp, 
  Award, 
  Heart 
} from 'lucide-react';
import { StatCard } from '../components/StatCard';
import { ImpactReport } from '../types';

interface ImpactViewProps {
  report: ImpactReport | null;
}

export const ImpactView: React.FC<ImpactViewProps> = ({ report }) => {
  if (!report) {
    return (
      <div className="glass-panel" style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-muted)' }}>
        Loading sustainability & impact analytics...
      </div>
    );
  }

  const totalServings = report.servingsDonated || 1;
  const deliveredPercent = ((report.servingsDelivered / totalServings) * 100).toFixed(1);
  const inTransitPercent = ((report.servingsInTransit / totalServings) * 100).toFixed(1);
  const availablePercent = ((report.servingsAvailable / totalServings) * 100).toFixed(1);
  const wastedPercent = ((report.servingsWasted / totalServings) * 100).toFixed(1);

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Header */}
      <div>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <span className="badge badge-emerald">
            <Leaf size={12} />
            Environmental & Social Governance (ESG)
          </span>
          <span className="badge badge-indigo">
            Campus Accountability
          </span>
        </div>
        <h2 style={{ fontSize: '1.875rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em' }}>
          Impact & Sustainability Accountability
        </h2>
        <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
          Detailed audit figures on food waste diversion, greenhouse gas mitigation, and community nourishment.
        </p>
      </div>

      {/* Main KPI Highlights */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem' }}>
        <StatCard
          title="Food Waste Diverted"
          value={`${report.foodSavedKg} kg`}
          subtitle="Rescued from landfills"
          icon={Leaf}
          variant="emerald"
          trend="0.35 kg/serving factor"
        />
        <StatCard
          title="CO₂e Avoided"
          value={`${report.co2AvoidedKg} kg`}
          subtitle="Emissions mitigation"
          icon={TrendingUp}
          variant="indigo"
          trend="2.5 kg CO₂e/kg food"
        />
        <StatCard
          title="Delivery Success"
          value={`${report.deliveryRatePercent}%`}
          subtitle={`${report.servingsDelivered} of ${report.servingsDonated} delivered`}
          icon={Heart}
          variant="cyan"
        />
        <StatCard
          title="Beneficiaries Served"
          value={report.recipientsServed}
          subtitle={`Across ${report.recipients} registered`}
          icon={Award}
          variant="amber"
        />
      </div>

      {/* Servings Lifecycle Distribution */}
      <div className="glass-panel" style={{ padding: '1.75rem' }}>
        <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff', marginBottom: '0.5rem' }}>
          Surplus Servings Distribution Breakdown
        </h3>
        <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
          Accountability metrics tracking total intake through delivery completion
        </p>

        {/* Stacked Progress Bar */}
        <div style={{
          height: '14px',
          borderRadius: '7px',
          background: 'rgba(255, 255, 255, 0.05)',
          overflow: 'hidden',
          display: 'flex',
          marginBottom: '1.25rem',
        }}>
          <div style={{ width: `${deliveredPercent}%`, background: 'var(--emerald)' }} title={`Delivered: ${deliveredPercent}%`} />
          <div style={{ width: `${inTransitPercent}%`, background: 'var(--indigo)' }} title={`In Transit: ${inTransitPercent}%`} />
          <div style={{ width: `${availablePercent}%`, background: 'var(--cyan)' }} title={`Available: ${availablePercent}%`} />
          <div style={{ width: `${wastedPercent}%`, background: 'var(--rose)' }} title={`Expired / Wasted: ${wastedPercent}%`} />
        </div>

        {/* Legend */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: 'var(--emerald)' }} />
            <div>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Delivered</p>
              <p style={{ fontSize: '1rem', fontWeight: 700, color: '#ffffff' }}>
                {report.servingsDelivered} ({deliveredPercent}%)
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: 'var(--indigo)' }} />
            <div>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>In Transit</p>
              <p style={{ fontSize: '1rem', fontWeight: 700, color: '#ffffff' }}>
                {report.servingsInTransit} ({inTransitPercent}%)
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: 'var(--cyan)' }} />
            <div>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Still Available</p>
              <p style={{ fontSize: '1rem', fontWeight: 700, color: '#ffffff' }}>
                {report.servingsAvailable} ({availablePercent}%)
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: 'var(--rose)' }} />
            <div>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Expired / Wasted</p>
              <p style={{ fontSize: '1rem', fontWeight: 700, color: '#ffffff' }}>
                {report.servingsWasted} ({wastedPercent}%)
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Leaderboards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '1.5rem' }}>
        
        {/* Top Donors */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <Award size={18} color="var(--emerald)" />
            <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>
              Top Campus Donors (Servings Delivered)
            </h3>
          </div>

          {report.topDonors.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', padding: '1rem', textAlign: 'center' }}>
              No deliveries recorded yet.
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {report.topDonors.map((d, idx) => (
                <div
                  key={d.id}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '0.75rem 1rem',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <span style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: idx === 0 ? 'rgba(245, 158, 11, 0.2)' : 'rgba(255, 255, 255, 0.08)',
                      color: idx === 0 ? '#fbbf24' : '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 800,
                      fontSize: '0.75rem',
                    }}>
                      #{idx + 1}
                    </span>
                    <div>
                      <p style={{ fontWeight: 600, color: '#ffffff', fontSize: '0.875rem' }}>{d.name}</p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{d.id}</p>
                    </div>
                  </div>

                  <span style={{ fontWeight: 800, color: 'var(--emerald)', fontSize: '1rem' }}>
                    {d.servings} <span style={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--text-muted)' }}>servings</span>
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Top Recipients */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <Award size={18} color="var(--indigo)" />
            <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>
              Top Recipient Beneficiaries
            </h3>
          </div>

          {report.topRecipients.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', padding: '1rem', textAlign: 'center' }}>
              No deliveries recorded yet.
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {report.topRecipients.map((r, idx) => (
                <div
                  key={r.id}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '0.75rem 1rem',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <span style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: idx === 0 ? 'rgba(99, 102, 241, 0.2)' : 'rgba(255, 255, 255, 0.08)',
                      color: idx === 0 ? '#818cf8' : '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 800,
                      fontSize: '0.75rem',
                    }}>
                      #{idx + 1}
                    </span>
                    <div>
                      <p style={{ fontWeight: 600, color: '#ffffff', fontSize: '0.875rem' }}>{r.name}</p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{r.id}</p>
                    </div>
                  </div>

                  <span style={{ fontWeight: 800, color: 'var(--indigo)', fontSize: '1rem' }}>
                    {r.servings} <span style={{ fontSize: '0.75rem', fontWeight: 500, color: 'var(--text-muted)' }}>servings</span>
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  );
};
