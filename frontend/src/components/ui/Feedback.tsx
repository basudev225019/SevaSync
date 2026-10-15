import type { ReactNode } from 'react';
import { clsx } from 'clsx';
import { AlertTriangle, Inbox } from 'lucide-react';
import { ApiError } from '../../services/api';
import { Button } from './Button';

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-2xl bg-white/[0.04]', className)} />;
}

export function EmptyState({ icon, title, children, action }: {
  icon?: ReactNode;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-3xl border border-dashed border-white/8 px-6 py-14 text-center">
      <div className="mb-4 grid size-12 place-items-center rounded-2xl bg-white/5 text-ink-300">
        {icon ?? <Inbox className="size-5" />}
      </div>
      <h3 className="text-base font-semibold">{title}</h3>
      {children && <p className="mt-1.5 max-w-sm text-sm text-ink-400">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong while loading this.';
  return (
    <div className="flex items-start gap-3 rounded-2xl border border-rose-400/20 bg-rose-500/5 p-4 text-sm text-rose-100">
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-rose-300" />
      <div className="flex-1">{message}</div>
      {onRetry && (
        <Button size="sm" variant="ghost" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}
