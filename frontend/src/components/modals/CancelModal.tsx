import React, { useState } from 'react';
import { X, AlertTriangle, Check, AlertCircle } from 'lucide-react';
import { Dispatch } from '../../types';

interface CancelModalProps {
  isOpen: boolean;
  onClose: () => void;
  dispatch: Dispatch | null;
  onConfirm: (dispatchId: string, reason: string) => Promise<void>;
}

export const CancelModal: React.FC<CancelModalProps> = ({
  isOpen,
  onClose,
  dispatch,
  onConfirm,
}) => {
  const [reason, setReason] = useState('Recipient closed early');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !dispatch) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('Please provide a reason.');
      return;
    }
    setError(null);
    try {
      setLoading(true);
      await onConfirm(dispatch.id, reason.trim());
      onClose();
    } catch (err: any) {
      setError(err.message || 'Dispatch cancellation failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content fade-in">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fb7185' }}>Cancel Dispatch</h3>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Servings will be returned to the surplus food pool if not expired
            </p>
          </div>
          <button onClick={onClose} className="btn btn-secondary btn-icon" style={{ padding: '0.375rem' }}>
            <X size={18} />
          </button>
        </div>

        {error && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.75rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(244, 63, 94, 0.12)',
            border: '1px solid rgba(244, 63, 94, 0.3)',
            color: '#fb7185',
            fontSize: '0.8125rem',
            marginBottom: '1rem',
          }}>
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="input-group">
            <label className="input-label">Cancellation Reason</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. Transport unavailable, Recipient closed early, Food quality concern"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary" disabled={loading}>
              Keep Dispatch
            </button>
            <button type="submit" className="btn btn-danger" disabled={loading}>
              <AlertTriangle size={16} />
              {loading ? 'Cancelling...' : 'Confirm Cancellation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
