import { useState } from 'react';
import { Star } from 'lucide-react';
import { clsx } from 'clsx';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { TextArea } from '../ui/Field';
import type { Dispatch } from '../../types';

/** Recipient confirms a delivery arrived and rates it (1-5 stars). */
export function DeliverModal({ dispatch, onClose, onConfirm, busy }: {
  dispatch: Dispatch | null;
  onClose: () => void;
  onConfirm: (rating: number, feedback: string) => void;
  busy?: boolean;
}) {
  const [rating, setRating] = useState(5);
  const [hover, setHover] = useState(0);
  const [feedback, setFeedback] = useState('');
  const shown = hover || rating;
  const words = ['', 'Poor', 'Okay', 'Good', 'Great', 'Excellent'];

  return (
    <Modal
      open={!!dispatch}
      onClose={onClose}
      title="Confirm delivery"
      subtitle={dispatch ? `${dispatch.servings} servings of ${dispatch.batch?.foodName ?? 'food'} from ${dispatch.donor?.name ?? 'a donor'}` : undefined}
      width="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Not yet</Button>
          <Button loading={busy} onClick={() => onConfirm(rating, feedback)}>Confirm & rate</Button>
        </>
      }
    >
      <p className="label">How was it?</p>
      <div className="flex items-center gap-1" onMouseLeave={() => setHover(0)}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            onClick={() => setRating(n)}
            onMouseEnter={() => setHover(n)}
            className="p-1 transition-transform hover:scale-110"
            aria-label={`${n} star${n > 1 ? 's' : ''}`}
            aria-pressed={rating === n}
          >
            <Star className={clsx('size-8 transition', n <= shown ? 'fill-saffron-400 text-saffron-400' : 'text-ink-500')} />
          </button>
        ))}
        <span className="ml-3 text-sm font-semibold text-saffron-300">{words[shown]}</span>
      </div>
      <TextArea className="mt-5" label="A word for the donor (optional)" value={feedback} onChange={(e) => setFeedback(e.target.value)} maxLength={280} placeholder="Still warm, the children loved it!" />
    </Modal>
  );
}
