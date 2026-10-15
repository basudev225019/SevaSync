import { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input } from '../ui/Field';
import type { Dispatch } from '../../types';

/** Coordinator cancels a dispatch. Food that never left the kitchen goes back into the matching pool. */
export function CancelModal({ dispatch, onClose, onCancel, busy }: {
  dispatch: Dispatch | null;
  onClose: () => void;
  onCancel: (reason: string) => void;
  busy?: boolean;
}) {
  const [reason, setReason] = useState('');
  const pickedUp = dispatch?.status === 'PICKED_UP';
  return (
    <Modal
      open={!!dispatch}
      onClose={onClose}
      title="Cancel this dispatch?"
      subtitle={dispatch ? `${dispatch.id} · ${dispatch.servings} servings to ${dispatch.recipient?.name ?? dispatch.recipientId}` : undefined}
      width="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Keep it</Button>
          <Button variant="danger" loading={busy} onClick={() => onCancel(reason)}>Cancel dispatch</Button>
        </>
      }
    >
      <p className="mb-4 text-sm text-ink-300">
        {pickedUp
          ? 'The food is already on its way, so it will count as lost. Only cancel if the delivery truly cannot happen.'
          : 'The servings go back to the kitchen and will be offered to another recipient automatically.'}
      </p>
      <Input label="Reason (shown on the audit trail)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={120} placeholder="Recipient closed for the evening" />
    </Modal>
  );
}
