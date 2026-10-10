import React, { useState } from 'react';
import { 
  Cpu, 
  Play, 
  Eye, 
  Clock, 
  AlertCircle, 
  CheckCircle2, 
  Users, 
  RefreshCw
} from 'lucide-react';
import { FoodBatch, MatchResult, Recipient } from '../types';

interface MatchingViewProps {
  batches: FoodBatch[];
  recipients: Recipient[];
  onPreviewMatching: () => Promise<MatchResult>;
  onRunMatching: () => Promise<MatchResult>;
}

export const MatchingView: React.FC<MatchingViewProps> = ({
  batches,
  recipients,
  onPreviewMatching,
  onRunMatching,
}) => {
  const [matchResult, setMatchResult] = useState<MatchResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const availableBatches = batches.filter((b) => b.status === 'AVAILABLE' && b.remainingServings > 0);
  const sortedBatches = [...availableBatches].sort(
    (a, b) => new Date(a.expiresAt).getTime() - new Date(b.expiresAt).getTime()
  );

  const sortedRecipients = [...recipients].sort((a, b) => {
    const needRank = { CRITICAL: 3, HIGH: 2, NORMAL: 1 };
    const diff = needRank[b.needLevel] - needRank[a.needLevel];
    if (diff !== 0) return diff;
    return (b.remainingCapacityToday || b.dailyCapacity) - (a.remainingCapacityToday || a.dailyCapacity);
  });

  const handlePreview = async () => {
    setError(null);
    try {
      setLoading(true);
      const res = await onPreviewMatching();
      setMatchResult(res);
    } catch (err: any) {
      setError(err.message || 'Matching preview failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRun = async () => {
    setError(null);
    try {
      setLoading(true);
      const res = await onRunMatching();
      setMatchResult(res);
    } catch (err: any) {
      setError(err.message || 'Matching execution failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Engine Banner */}
      <div
        className="glass-panel"
        style={{
          padding: '2rem',
          background: 'radial-gradient(ellipse at top right, rgba(99, 102, 241, 0.15) 0%, rgba(15, 23, 42, 0.8) 70%)',
          border: '1px solid rgba(99, 102, 241, 0.25)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1.5rem',
        }}
      >
        <div style={{ maxWidth: '640px' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <span className="badge badge-indigo">
              <Cpu size={12} />
              Double PriorityQueue Engine
            </span>
            <span className="badge badge-emerald">
              Complexity: O((B+R+A) log(B+R))
            </span>
          </div>
          <h2 style={{ fontSize: '1.875rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em', marginBottom: '0.5rem' }}>
            Smart Priority Matching Engine
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            Pairs the soonest-expiring surplus batches with the neediest recipient facilities. If an organization's remaining daily capacity is reached, batches automatically split across the next priority recipient.
          </p>
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            onClick={handlePreview}
            disabled={loading || availableBatches.length === 0}
            className="btn btn-secondary"
          >
            <Eye size={16} />
            Simulate (Preview)
          </button>
          <button
            onClick={handleRun}
            disabled={loading || availableBatches.length === 0}
            className="btn btn-accent"
          >
            {loading ? <RefreshCw size={16} className="spin" /> : <Play size={16} />}
            Commit Smart Matching
          </button>
        </div>
      </div>

      {error && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
          padding: '1rem',
          borderRadius: 'var(--radius-md)',
          background: 'rgba(244, 63, 94, 0.12)',
          border: '1px solid rgba(244, 63, 94, 0.3)',
          color: '#fb7185',
          fontSize: '0.875rem',
        }}>
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Match Result Display */}
      {matchResult && (
        <div
          className="glass-panel fade-in"
          style={{
            padding: '1.75rem',
            border: `1px solid ${matchResult.committed ? 'var(--border-highlight)' : 'rgba(99, 102, 241, 0.4)'}`,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <div style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: matchResult.committed ? 'rgba(16, 185, 129, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
                <CheckCircle2 size={20} color={matchResult.committed ? 'var(--emerald)' : 'var(--indigo)'} />
              </div>
              <div>
                <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>
                  {matchResult.committed ? 'Matching Committed to SQLite' : 'Dry Run Preview Result'}
                </h3>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  {matchResult.committed ? 'Dispatches created & saved into database' : 'Simulated distribution without persisting changes'}
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'center' }}>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Allocated:</span>
                <p style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--emerald)' }}>
                  {matchResult.allocatedServings} servings
                </p>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Unallocated:</span>
                <p style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--amber)' }}>
                  {matchResult.unallocatedServings} servings
                </p>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Dispatches:</span>
                <p style={{ fontSize: '1.25rem', fontWeight: 800, color: '#ffffff' }}>
                  {matchResult.dispatches.length}
                </p>
              </div>
            </div>
          </div>

          {/* Allocation Table */}
          <div className="table-container">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>Dispatch ID</th>
                  <th>Food Batch</th>
                  <th>Recipient Beneficiary</th>
                  <th>Assigned Servings</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {matchResult.dispatches.map((d) => (
                  <tr key={d.id}>
                    <td style={{ fontWeight: 700, color: '#ffffff' }}>{d.id}</td>
                    <td>{d.batchId}</td>
                    <td style={{ color: 'var(--indigo)' }}>{d.recipientId}</td>
                    <td>
                      <span style={{ fontWeight: 800, color: 'var(--emerald)' }}>{d.servings}</span> servings
                    </td>
                    <td>
                      <span className="badge badge-indigo">{d.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Two Queues Visualization */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '1.5rem' }}>
        
        {/* Priority Queue 1: Food Batches */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Clock size={18} color="var(--amber)" />
              <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>
                Batch Priority Queue (Earliest Expiry)
              </h3>
            </div>
            <span className="badge badge-amber">{sortedBatches.length} Eligible</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {sortedBatches.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', padding: '1rem', textAlign: 'center' }}>
                No surplus batches available for matching.
              </p>
            ) : (
              sortedBatches.map((b, idx) => (
                <div
                  key={b.id}
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
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: 'rgba(255, 255, 255, 0.08)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                    }}>
                      #{idx + 1}
                    </span>
                    <div>
                      <p style={{ fontWeight: 600, color: '#ffffff', fontSize: '0.875rem' }}>{b.foodName}</p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        {b.id} • {b.remainingServings} servings left
                      </p>
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <span className="badge badge-amber" style={{ fontSize: '0.65rem' }}>
                      Exp: {b.expiresAt.split('T')[1]}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Priority Queue 2: Recipients */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Users size={18} color="var(--indigo)" />
              <h3 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#ffffff' }}>
                Recipient Priority Queue (Need & Capacity)
              </h3>
            </div>
            <span className="badge badge-indigo">{sortedRecipients.length} Registered</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {sortedRecipients.map((r, idx) => (
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
                    width: '24px',
                    height: '24px',
                    borderRadius: '50%',
                    background: 'rgba(255, 255, 255, 0.08)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                  }}>
                    #{idx + 1}
                  </span>
                  <div>
                    <p style={{ fontWeight: 600, color: '#ffffff', fontSize: '0.875rem' }}>{r.name}</p>
                    <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {r.id} • {r.recipientType}
                    </p>
                  </div>
                </div>

                <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.25rem' }}>
                  <span className={`badge ${r.needLevel === 'CRITICAL' ? 'badge-rose' : r.needLevel === 'HIGH' ? 'badge-amber' : 'badge-emerald'}`} style={{ fontSize: '0.65rem' }}>
                    {r.needLevel}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    Cap: {r.remainingCapacityToday ?? r.dailyCapacity} / {r.dailyCapacity}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
};
