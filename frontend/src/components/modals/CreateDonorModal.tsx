import React, { useState } from 'react';
import { X, AlertCircle, Check } from 'lucide-react';

interface CreateDonorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    name: string;
    phone: string;
    email?: string;
    address?: string;
    donorType: string;
  }) => Promise<void>;
}

export const CreateDonorModal: React.FC<CreateDonorModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [donorType, setDonorType] = useState('HOSTEL_MESS');
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
        donorType,
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to register donor');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content fade-in">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#ffffff' }}>Register Food Donor</h3>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Add a campus hostel mess, cafeteria, or event team
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
            <label className="input-label">Facility / Organization Name</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. Boys Hostel 2 Mess, Central Canteen"
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
                placeholder="9876543210"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                required
              />
            </div>

            <div className="input-group">
              <label className="input-label">Donor Type</label>
              <select
                className="select-field"
                value={donorType}
                onChange={(e) => setDonorType(e.target.value)}
              >
                <option value="HOSTEL_MESS">Hostel Mess</option>
                <option value="CANTEEN">Canteen / Food Court</option>
                <option value="CATERER">Campus Caterer</option>
                <option value="EVENT_COORDINATOR">Event / Fest Committee</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
          </div>

          <div className="input-group">
            <label className="input-label">E-mail (Optional)</label>
            <input
              type="email"
              className="input-field"
              placeholder="mess.coordinator@campus.edu"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="input-group">
            <label className="input-label">Campus Location / Address</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. North Campus, Block B, Ground Floor"
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
              {loading ? 'Registering...' : 'Register Donor'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
