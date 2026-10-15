import type { ReactNode } from 'react';
import SplitText from './reactbits/SplitText';

/** Page title with a ReactBits SplitText entrance, optional eyebrow, description and actions. */
export function PageHeader({ eyebrow, title, description, actions, accent = '#f2a950' }: {
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em]" style={{ color: accent }}>{eyebrow}</p>}
        <SplitText
          key={title}
          text={title}
          tag="h1"
          textAlign="left"
          className="font-display text-3xl font-semibold tracking-tight text-ink-100 sm:text-[34px]"
          delay={22}
          duration={0.8}
          trigger="load"
        />
        {description && <p className="mt-2 max-w-2xl text-sm text-ink-400">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
