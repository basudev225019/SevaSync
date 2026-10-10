import React, { useState } from 'react';
import { 
  Building2, 
  HeartHandshake, 
  Plus, 
  Phone, 
  Mail, 
  MapPin, 
  Calendar 
} from 'lucide-react';
import { Donor, Recipient } from '../types';

interface PartnersViewProps {
  donors: Donor[];
  recipients: Recipient[];
  onOpenCreateDonor: () => void;
  onOpenCreateRecipient: () => void;
}

export const PartnersView: React.FC<PartnersViewProps> = ({
  donors,
  recipients,
  onOpenCreateDonor,
  onOpenCreateRecipient,
}) => {
  const [subTab, setSubTab] = useState<'recipients' | 'donors'>('recipients');

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Header bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em' }}>
            Campus Community Partners
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Network of registered food donors and community beneficiary shelters
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {subTab === 'donors' ? (
            <button onClick={onOpenCreateDonor} className="btn btn-primary">
              <Plus size={16} />
              Register New Donor
            </button>
          ) : (
            <button onClick={onOpenCreateRecipient} className="btn btn-primary">
              <Plus size={16} />
              Register New Recipient
            </button>
          )}
        </div>
      </div>

      {/* Subtab Switcher */}
      <div style={{ display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.75rem' }}>
        <button
          onClick={() => setSubTab('recipients')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.625rem 1.25rem',
            borderRadius: 'var(--radius-md)',
            background: subTab === 'recipients' ? 'rgba(99, 102, 241, 0.15)' : 'transparent',
            border: subTab === 'recipients' ? '1px solid rgba(99, 102, 241, 0.3)' : '1px solid transparent',
            color: subTab === 'recipients' ? '#818cf8' : 'var(--text-secondary)',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          <HeartHandshake size={18} />
          Recipient Beneficiaries ({recipients.length})
        </button>

        <button
          onClick={() => setSubTab('donors')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.625rem 1.25rem',
            borderRadius: 'var(--radius-md)',
            background: subTab === 'donors' ? 'rgba(16, 185, 129, 0.15)' : 'transparent',
            border: subTab === 'donors' ? '1px solid rgba(16, 185, 129, 0.3)' : '1px solid transparent',
            color: subTab === 'donors' ? '#34d399' : 'var(--text-secondary)',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          <Building2 size={18} />
          Campus Donors ({donors.length})
        </button>
      </div>

      {/* Recipients View */}
      {subTab === 'recipients' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          {recipients.map((r) => {
            const used = r.dailyCapacity - (r.remainingCapacityToday ?? r.dailyCapacity);
            const percent = Math.min(100, (used / r.dailyCapacity) * 100);
            return (
              <div
                key={r.id}
                className="glass-panel"
                style={{
                  padding: '1.5rem',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '1rem',
                  position: 'relative',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)' }}>{r.id}</span>
                    <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff', marginTop: '0.125rem' }}>
                      {r.name}
                    </h3>
                    <span className="badge badge-indigo" style={{ fontSize: '0.65rem', marginTop: '0.25rem' }}>
                      {r.recipientType}
                    </span>
                  </div>

                  <span className={`badge ${r.needLevel === 'CRITICAL' ? 'badge-rose' : r.needLevel === 'HIGH' ? 'badge-amber' : 'badge-emerald'}`}>
                    {r.needLevel} NEED
                  </span>
                </div>

                {/* Capacity Meter */}
                <div style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-md)',
                  padding: '0.875rem',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: '0.375rem' }}>
                    <span style={{ color: 'var(--text-secondary)' }}>Today's Capacity Absorbed:</span>
                    <span style={{ fontWeight: 700, color: '#ffffff' }}>
                      {used} / {r.dailyCapacity} servings
                    </span>
                  </div>
                  <div style={{ height: '6px', background: 'rgba(255,255,255,0.06)', borderRadius: '3px', overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${percent}%`, background: 'var(--indigo)', borderRadius: '3px' }} />
                  </div>
                  <p style={{ fontSize: '0.7rem', color: 'var(--emerald)', marginTop: '0.375rem', textAlign: 'right' }}>
                    {r.remainingCapacityToday ?? r.dailyCapacity} servings free today
                  </p>
                </div>

                {/* Contact info */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Phone size={14} color="var(--text-muted)" />
                    <span>{r.phone}</span>
                  </div>
                  {r.email && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <Mail size={14} color="var(--text-muted)" />
                      <span>{r.email}</span>
                    </div>
                  )}
                  {r.address && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <MapPin size={14} color="var(--text-muted)" />
                      <span>{r.address}</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Donors View */}
      {subTab === 'donors' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          {donors.map((d) => (
            <div
              key={d.id}
              className="glass-panel"
              style={{
                padding: '1.5rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)' }}>{d.id}</span>
                  <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff', marginTop: '0.125rem' }}>
                    {d.name}
                  </h3>
                </div>
                <span className="badge badge-emerald">
                  {d.donorType}
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Phone size={14} color="var(--text-muted)" />
                  <span>{d.phone}</span>
                </div>
                {d.email && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Mail size={14} color="var(--text-muted)" />
                    <span>{d.email}</span>
                  </div>
                )}
                {d.address && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <MapPin size={14} color="var(--text-muted)" />
                    <span>{d.address}</span>
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  <Calendar size={14} />
                  <span>Joined: {d.createdAt.replace('T', ' ')}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
