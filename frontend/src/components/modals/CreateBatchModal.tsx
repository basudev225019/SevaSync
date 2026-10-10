import React, { useState } from 'react';
import { X, AlertCircle, ShieldCheck, Check } from 'lucide-react';
import { Donor, FoodCategory } from '../../types';

interface CreateBatchModalProps {
  isOpen: boolean;
  onClose: () => void;
  donors: Donor[];
  onSubmit: (data: {
    donorId: string;
    foodName: string;
    category: string;
    vegetarian: boolean;
    servings: number;
    shelfLifeHours: number;
  }) => Promise<void>;
}

const CATEGORY_MAX_HOURS: Record<FoodCategory, number> = {
  COOKED_MEAL: 4,
  BEVERAGE: 8,
  BAKERY: 12,
  FRUITS_VEGETABLES: 24,
  PACKAGED: 48,
};

export const CreateBatchModal: React.FC<CreateBatchModalProps> = ({
  isOpen,
  onClose,
  donors,
  onSubmit,
}) => {
  const [donorId, setDonorId] = useState(donors[0]?.id || '');
  const [foodName, setFoodName] = useState('');
  const [category, setCategory] = useState<FoodCategory>('COOKED_MEAL');
  const [vegetarian, setVegetarian] = useState(true);
  const [servings, setServings] = useState(50);
  const [shelfLifeHours, setShelfLifeHours] = useState(3);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const maxHours = CATEGORY_MAX_HOURS[category];

  const handleCategoryChange = (cat: FoodCategory) => {
    setCategory(cat);
    if (shelfLifeHours > CATEGORY_MAX_HOURS[cat]) {
      setShelfLifeHours(CATEGORY_MAX_HOURS[cat]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!donorId) {
      setError('Please select a donor.');
      return;
    }
    if (!foodName.trim()) {
      setError('Food name is required.');
      return;
    }
    if (shelfLifeHours > maxHours) {
      setError(`Maximum shelf life for ${category.replace('_', ' ')} is ${maxHours} hours.`);
      return;
    }

    try {
      setLoading(true);
      await onSubmit({
        donorId,
        foodName: foodName.trim(),
        category,
        vegetarian,
        servings: Number(servings),
        shelfLifeHours: Number(shelfLifeHours),
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to submit batch');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content fade-in">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#ffffff' }}>Log Surplus Food Batch</h3>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Surplus intake with automated freshness validation
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
            <label className="input-label">Donor (Hostel / Canteen)</label>
            <select
              className="select-field"
              value={donorId}
              onChange={(e) => setDonorId(e.target.value)}
              required
            >
              <option value="">-- Select Donor --</option>
              {donors.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.id} - {d.donorType})
                </option>
              ))}
            </select>
          </div>

          <div className="input-group">
            <label className="input-label">Food Item Name</label>
            <input
              type="text"
              className="input-field"
              placeholder="e.g. Veg Biryani, Chana Masala, Sandwiches"
              value={foodName}
              onChange={(e) => setFoodName(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="input-group">
              <label className="input-label">Food Category</label>
              <select
                className="select-field"
                value={category}
                onChange={(e) => handleCategoryChange(e.target.value as FoodCategory)}
              >
                <option value="COOKED_MEAL">Cooked Meal (Max 4h)</option>
                <option value="BEVERAGE">Beverage (Max 8h)</option>
                <option value="BAKERY">Bakery (Max 12h)</option>
                <option value="FRUITS_VEGETABLES">Fruit / Veg (Max 24h)</option>
                <option value="PACKAGED">Packaged (Max 48h)</option>
              </select>
            </div>

            <div className="input-group">
              <label className="input-label">Servings Count</label>
              <input
                type="number"
                min="1"
                max="5000"
                className="input-field"
                value={servings}
                onChange={(e) => setServings(Number(e.target.value))}
                required
              />
            </div>
          </div>

          <div className="input-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label className="input-label">Remaining Shelf Life (Hours)</label>
              <span style={{ fontSize: '0.75rem', color: 'var(--emerald)' }}>
                Max safety limit: {maxHours}h
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <input
                type="range"
                min="0.5"
                max={maxHours}
                step="0.5"
                style={{ flex: 1, accentColor: 'var(--emerald)' }}
                value={shelfLifeHours}
                onChange={(e) => setShelfLifeHours(Number(e.target.value))}
              />
              <span style={{
                minWidth: '55px',
                textAlign: 'right',
                fontWeight: 700,
                color: '#ffffff',
                fontFamily: 'monospace',
                fontSize: '0.9375rem',
              }}>
                {shelfLifeHours} hrs
              </span>
            </div>
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.875rem' }}>
            <input
              type="checkbox"
              checked={vegetarian}
              onChange={(e) => setVegetarian(e.target.checked)}
              style={{ accentColor: 'var(--emerald)', width: '16px', height: '16px' }}
            />
            <span style={{ color: 'var(--text-primary)' }}>100% Vegetarian</span>
          </label>

          <div style={{
            background: 'rgba(16, 185, 129, 0.06)',
            border: '1px solid rgba(16, 185, 129, 0.2)',
            borderRadius: 'var(--radius-md)',
            padding: '0.75rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.5rem',
            fontSize: '0.75rem',
            color: 'var(--text-secondary)',
          }}>
            <ShieldCheck size={16} color="var(--emerald)" style={{ flexShrink: 0, marginTop: '2px' }} />
            <span>
              <strong>Safety Validator:</strong> Will reject batches with less than 30 minutes of shelf life or exceeding the {maxHours}h consumption threshold.
            </span>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary" disabled={loading}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              <Check size={16} />
              {loading ? 'Submitting...' : 'Confirm & Log Batch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
