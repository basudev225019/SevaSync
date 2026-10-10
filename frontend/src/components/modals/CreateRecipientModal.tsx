import React, { useState } from 'react';
import { X, AlertCircle, Check } from 'lucide-react';
import { NeedLevel, RecipientType } from '../../types';

interface CreateRecipientModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    name: string;
    phone: string;
    email?: string;
    address?: string;
    recipientType: string;
    needLevel: string;
    dailyCapacity: number;
  }) => Promise<void>;
}

export const CreateRecipientModal: React.FC<CreateRecipientModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [recipientType, setRecipientType] = useState<RecipientType>('SHELTER');
  const [needLevel, setNeedLevel] = useState<NeedLevel>('HIGH');
  const [dailyCapacity, setDailyCapacity] = useState(100);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      setLoading(true);
      await onSubmit({
        name: name.trim(),
        phone: phone.trim(),
        email: email.trim() || undefined,
        address: address.trim() || undefined,
        recipientType,
        needLevel,
        dailyCapacity: Number(dailyCapacity),
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to register recipient');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content fade-in">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#ffffff' }}>Register Recipient Beneficiary</h3>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Add an orphanage, shelter, community kitchen, or NGO
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
            <label className="input-label">Organization / Shelter Name</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. Asha Bhavan Orphanage, Hope Night Shelter"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="input-group">
              <label className="input-label">Phone Number (10 digits)</label>
              <input
                type="tel"
                className="input-field"
                placeholder="9812345678"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                required
              />
            </div>

            <div className="input-group">
              <label className="input-label">Recipient Category</label>
              <select
                className="select-field"
                value={recipientType}
                onChange={(e) => setRecipientType(e.target.value as RecipientType)}
              >
                <option value="ORPHANAGE">Orphanage</option>
                <option value="SHELTER">Homeless Shelter</option>
                <option value="OLD_AGE_HOME">Old Age Home</option>
                <option value="COMMUNITY_KITCHEN">Community Kitchen</option>
                <option value="NGO">Relief NGO</option>
                <option value="SLUM_SCHOOL">Slum School / Center</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="input-group">
              <label className="input-label">Urgency Need Level</label>
              <select
                className="select-field"
                value={needLevel}
                onChange={(e) => setNeedLevel(e.target.value as NeedLevel)}
              >
                <option value="CRITICAL">CRITICAL (Top Priority in Engine)</option>
                <option value="HIGH">HIGH Priority</option>
                <option value="NORMAL">NORMAL Priority</option>
              </select>
            </div>

            <div className="input-group">
              <label className="input-label">Max Daily Servings Capacity</label>
              <input
                type="number"
                min="1"
                max="5000"
                className="input-field"
                value={dailyCapacity}
                onChange={(e) => setDailyCapacity(Number(e.target.value))}
                required
              />
            </div>
          </div>

          <div className="input-group">
            <label className="input-label">Delivery Address / Destination</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. 14 Gandhi Nagar, Near Town Station"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary" disabled={loading}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              <Check size={16} />
              {loading ? 'Registering...' : 'Register Recipient'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
