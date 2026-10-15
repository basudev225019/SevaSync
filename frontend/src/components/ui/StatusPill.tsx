import { Badge } from './Badge';
import { batchStatusLabel, dispatchStatusLabel, needLabel } from '../../lib/format';
import { batchColor, dispatchColor, needColor } from '../../lib/theme';
import type { BatchStatus, DispatchStatus, NeedLevel } from '../../types';

export function DispatchPill({ status }: { status: DispatchStatus }) {
  const moving = status === 'CLAIMED' || status === 'PICKED_UP';
  return (
    <Badge color={dispatchColor[status]} dot pulse={moving}>
      {dispatchStatusLabel[status]}
    </Badge>
  );
}

export function BatchPill({ status }: { status: BatchStatus }) {
  return (
    <Badge color={batchColor[status]} dot>
      {batchStatusLabel[status]}
    </Badge>
  );
}

export function NeedPill({ level }: { level: NeedLevel }) {
  return (
    <Badge color={needColor[level]} dot pulse={level === 'CRITICAL'}>
      {needLabel[level]}
    </Badge>
  );
}
