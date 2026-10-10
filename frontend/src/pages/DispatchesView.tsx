import React, { useState } from 'react';
import { 
  Check, 
  X, 
  Search, 
  Filter
} from 'lucide-react';
import { Dispatch, DispatchStatus } from '../types';

interface DispatchesViewProps {
  dispatches: Dispatch[];
  onOpenDeliverModal: (dispatch: Dispatch) => void;
  onOpenCancelModal: (dispatch: Dispatch) => void;
}

export const DispatchesView: React.FC<DispatchesViewProps> = ({
  dispatches,
  onOpenDeliverModal,
  onOpenCancelModal,
}) => {
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const filteredDispatches = dispatches.filter((d) => {
    const matchesStatus = filterStatus === 'ALL' || d.status === filterStatus;
    const matchesSearch = d.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          d.batchId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          d.recipientId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          d.volunteer.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesStatus && matchesSearch;
  });

  const getStatusBadge = (status: DispatchStatus) => {
    switch (status) {
      case 'ASSIGNED':
        return <span className="badge badge-indigo">Assigned (In Transit)</span>;
      case 'DELIVERED':
        return <span className="badge badge-emerald">Delivered</span>;
      case 'CANCELLED':
        return <span className="badge badge-rose">Cancelled</span>;
      default:
        return <span className="badge badge-slate">{status}</span>;
    }
  };

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Header bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em' }}>
            Volunteer Dispatches & Deliveries
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Track active hand-overs, verify volunteer drop-offs, and monitor distribution lifecycle
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-panel" style={{ padding: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, minWidth: '240px' }}>
          <Search size={18} color="var(--text-muted)" />
          <input
            type="text"
            className="input-field"
            placeholder="Search dispatch ID, batch, recipient, volunteer..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ border: 'none', background: 'transparent', padding: '0.25rem' }}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <Filter size={16} color="var(--text-muted)" />
          {['ALL', 'ASSIGNED', 'DELIVERED', 'CANCELLED'].map((s) => (
            <button
              key={s}
              onClick={() => setFilterStatus(s)}
              style={{
                background: filterStatus === s ? 'rgba(255, 255, 255, 0.1)' : 'transparent',
                border: filterStatus === s ? '1px solid rgba(255, 255, 255, 0.2)' : '1px solid transparent',
                borderRadius: 'var(--radius-md)',
                color: filterStatus === s ? '#ffffff' : 'var(--text-secondary)',
                padding: '0.375rem 0.75rem',
                fontSize: '0.75rem',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Dispatches Table */}
      <div className="table-container glass-panel">
        <table className="custom-table">
          <thead>
            <tr>
              <th>Dispatch ID</th>
              <th>Batch</th>
              <th>Recipient</th>
              <th>Servings</th>
              <th>Assigned At</th>
              <th>Delivered At / Volunteer</th>
              <th>Status</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredDispatches.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                  No dispatches found. Run Smart Matching to generate assignments!
                </td>
              </tr>
            ) : (
              filteredDispatches.map((d) => (
                <tr key={d.id}>
                  <td style={{ fontWeight: 700, color: '#ffffff' }}>{d.id}</td>
                  <td>{d.batchId}</td>
                  <td style={{ color: 'var(--indigo)', fontWeight: 600 }}>{d.recipientId}</td>
                  <td>
                    <span style={{ fontWeight: 800, color: 'var(--emerald)', fontSize: '0.9375rem' }}>
                      {d.servings}
                    </span>
                    <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginLeft: '0.25rem' }}>
                      servings
                    </span>
                  </td>
                  <td style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                    {d.assignedAt.replace('T', ' ')}
                  </td>
                  <td style={{ fontSize: '0.8125rem' }}>
                    {d.status === 'DELIVERED' ? (
                      <div>
                        <span style={{ color: 'var(--emerald)', fontWeight: 600 }}>
                          {d.volunteer || 'Volunteer'}
                        </span>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {d.deliveredAt?.replace('T', ' ')}
                        </div>
                      </div>
                    ) : d.status === 'CANCELLED' ? (
                      <span style={{ color: '#fb7185', fontSize: '0.75rem' }}>
                        {d.note || 'Cancelled'}
                      </span>
                    ) : (
                      <span style={{ color: 'var(--text-muted)' }}>Pending Delivery</span>
                    )}
                  </td>
                  <td>{getStatusBadge(d.status)}</td>
                  <td style={{ textAlign: 'right' }}>
                    {d.status === 'ASSIGNED' ? (
                      <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                        <button
                          onClick={() => onOpenDeliverModal(d)}
                          className="btn btn-primary btn-sm"
                          title="Confirm Volunteer Hand-over"
                        >
                          <Check size={14} />
                          Deliver
                        </button>
                        <button
                          onClick={() => onOpenCancelModal(d)}
                          className="btn btn-danger btn-sm"
                          title="Cancel Dispatch"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ) : (
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>Closed</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
