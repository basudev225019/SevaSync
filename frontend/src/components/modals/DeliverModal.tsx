import React, { useState } from 'react';
import { X, Check, AlertCircle } from 'lucide-react';
import { Dispatch } from '../../types';

interface DeliverModalProps {
  isOpen: boolean;
  onClose: () => void;
  dispatch: Dispatch | null;
  onConfirm: (dispatchId: string, volunteer: string) => Promise<void>;
}

export const DeliverModal: React.FC<DeliverModalProps> = ({
  isOpen,
  onClose,
  dispatch,
  onConfirm,
}) => {
  const [volunteer, setVolunteer] = useState('Ravi Kumar');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !dispatch) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!volunteer.trim()) {
      setError('Volunteer name is required.');
      return;
    }
    setError(null);
    try {
      setLoading(true);
      await onConfirm(dispatch.id, volunteer.trim());
      onClose();
    } catch (err: any) {
      setError(err.message || 'Delivery confirmation failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content fade-in">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#ffffff' }}>Confirm Food Hand-Over</h3>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Mark dispatch as successfully delivered to beneficiary
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

        <div style={{
          background: 'rgba(15, 23, 42, 0.8)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-md)',
          padding: '1rem',
          marginBottom: '1.25rem',
          display: 'flex',
          flexDirection: 'column',
          gap: '0.5rem',
          fontSize: '0.875rem',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Dispatch ID:</span>
            <span style={{ fontWeight: 700, color: '#ffffff' }}>{dispatch.id}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Batch:</span>
            <span>{dispatch.batchId}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Recipient:</span>
            <span>{dispatch.recipientId}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Servings:</span>
            <span style={{ color: 'var(--emerald)', fontWeight: 700 }}>{dispatch.servings} servings</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="input-group">
            <label className="input-label">Volunteer / Driver Name</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. Ravi, Student Volunteer Squad A"
              value={volunteer}
              onChange={(e) => setVolunteer(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary" disabled={loading}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              <Check size={16} />
              {loading ? 'Recording Delivery...' : 'Confirm Delivery'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
