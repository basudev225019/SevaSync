import React, { useState } from 'react';
import { 
  Plus, 
  Search, 
  Clock, 
  Filter
} from 'lucide-react';
import { BatchStatus, FoodBatch } from '../types';

interface BatchesViewProps {
  batches: FoodBatch[];
  onOpenCreateBatch: () => void;
}

export const BatchesView: React.FC<BatchesViewProps> = ({
  batches,
  onOpenCreateBatch,
}) => {
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const filteredBatches = batches.filter((b) => {
    const matchesStatus = filterStatus === 'ALL' || b.status === filterStatus;
    const matchesSearch = b.foodName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          b.donorId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          b.id.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesStatus && matchesSearch;
  });

  const getStatusBadge = (status: BatchStatus) => {
    switch (status) {
      case 'AVAILABLE':
        return <span className="badge badge-emerald">Available</span>;
      case 'ALLOCATED':
        return <span className="badge badge-indigo">Allocated</span>;
      case 'COMPLETED':
        return <span className="badge badge-cyan">Completed</span>;
      case 'EXPIRED':
        return <span className="badge badge-rose">Expired</span>;
      default:
        return <span className="badge badge-slate">{status}</span>;
    }
  };

  const getCategoryBadge = (cat: string) => {
    switch (cat) {
      case 'COOKED_MEAL': return 'Cooked Meal (4h)';
      case 'BEVERAGE': return 'Beverage (8h)';
      case 'BAKERY': return 'Bakery (12h)';
      case 'FRUITS_VEGETABLES': return 'Produce (24h)';
      case 'PACKAGED': return 'Packaged (48h)';
      default: return cat;
    }
  };

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Header bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em' }}>
            Surplus Food Batches
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Logged surplus batches tracked by remaining consumption shelf life
          </p>
        </div>

        <button onClick={onOpenCreateBatch} className="btn btn-primary">
          <Plus size={16} />
          Log Surplus Food Batch
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-panel" style={{ padding: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, minWidth: '240px' }}>
          <Search size={18} color="var(--text-muted)" />
          <input
            type="text"
            className="input-field"
            placeholder="Search food name, donor ID, batch ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ border: 'none', background: 'transparent', padding: '0.25rem' }}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <Filter size={16} color="var(--text-muted)" />
          {['ALL', 'AVAILABLE', 'ALLOCATED', 'COMPLETED', 'EXPIRED'].map((s) => (
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

      {/* Batches Table */}
      <div className="table-container glass-panel">
        <table className="custom-table">
          <thead>
            <tr>
              <th>Batch ID</th>
              <th>Food Item</th>
              <th>Category</th>
              <th>Donor</th>
              <th>Servings (Left / Total)</th>
              <th>Prepared At</th>
              <th>Expires At</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {filteredBatches.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                  No food batches matching criteria.
                </td>
              </tr>
            ) : (
              filteredBatches.map((b) => (
                <tr key={b.id}>
                  <td style={{ fontWeight: 700, color: '#ffffff' }}>{b.id}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <span style={{ fontWeight: 600 }}>{b.foodName}</span>
                      {b.vegetarian && (
                        <span className="badge badge-emerald" style={{ fontSize: '0.625rem', padding: '0.125rem 0.375rem' }}>
                          VEG
                        </span>
                      )}
                    </div>
                  </td>
                  <td>
                    <span className="badge badge-slate" style={{ fontSize: '0.7rem' }}>
                      {getCategoryBadge(b.category)}
                    </span>
                  </td>
                  <td style={{ color: 'var(--text-secondary)' }}>{b.donorId}</td>
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', minWidth: '120px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
                        <span style={{ fontWeight: 700, color: b.remainingServings > 0 ? 'var(--emerald)' : 'var(--text-muted)' }}>
                          {b.remainingServings}
                        </span>
                        <span style={{ color: 'var(--text-muted)' }}>/ {b.servings}</span>
                      </div>
                      <div style={{ height: '4px', background: 'rgba(255,255,255,0.06)', borderRadius: '2px', overflow: 'hidden' }}>
                        <div
                          style={{
                            height: '100%',
                            width: `${(b.remainingServings / b.servings) * 100}%`,
                            background: b.status === 'EXPIRED' ? 'var(--rose)' : 'var(--emerald)',
                            borderRadius: '2px',
                          }}
                        />
                      </div>
                    </div>
                  </td>
                  <td style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                    {b.preparedAt.replace('T', ' ')}
                  </td>
                  <td style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                      <Clock size={14} color="var(--amber)" />
                      {b.expiresAt.replace('T', ' ')}
                    </div>
                  </td>
                  <td>{getStatusBadge(b.status)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
