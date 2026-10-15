import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '../services/api';
import { errorMessage } from '../lib/errors';
import type { Dispatch } from '../types';

type Step = 'claim' | 'release' | 'pickup' | 'deliver';

const DONE: Record<Step, string> = {
  claim: 'Pickup claimed. Head to the kitchen!',
  release: 'Pickup released back to the board',
  pickup: 'Picked up. Now deliver it while it is fresh',
  deliver: 'Delivered! Thank you 💛',
};

/** Volunteer life-cycle mutations with cache refresh and friendly toasts. */
export function useDispatchActions() {
  const qc = useQueryClient();
  const refresh = () => {
    for (const key of ['dispatches', 'pickups', 'network', 'batches', 'me']) qc.invalidateQueries({ queryKey: [key] });
  };

  const step = useMutation({
    mutationFn: ({ id, action }: { id: string; action: Step }) => api.dispatchAction(id, action),
    onSuccess: (_d: Dispatch, v) => {
      toast.success(DONE[v.action]);
      refresh();
    },
    onError: (e) => {
      toast.error(errorMessage(e));
      refresh();
    },
  });

  const confirm = useMutation({
    mutationFn: ({ id, rating, feedback }: { id: string; rating: number; feedback: string }) =>
      api.confirmDelivery(id, rating, feedback),
    onSuccess: () => {
      toast.success('Thanks for confirming. The donor will see your rating.');
      refresh();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const cancel = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => api.cancelDispatch(id, reason),
    onSuccess: () => {
      toast.success('Dispatch cancelled; the food went back into the pool.');
      refresh();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return { step, confirm, cancel };
}
