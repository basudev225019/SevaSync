import { clsx } from 'clsx';
import { initials } from '../../lib/format';
import { roleColor } from '../../lib/theme';
import type { Role } from '../../types';

export function Avatar({ name, role, size = 36, className }: { name: string; role: Role; size?: number; className?: string }) {
  const color = roleColor[role];
  return (
    <span
      className={clsx('inline-grid shrink-0 place-items-center rounded-full font-display font-semibold', className)}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.36,
        color,
        background: `radial-gradient(circle at 30% 25%, color-mix(in oklab, ${color} 35%, transparent), color-mix(in oklab, ${color} 10%, transparent))`,
        boxShadow: `inset 0 0 0 1px color-mix(in oklab, ${color} 35%, transparent)`,
      }}
      aria-hidden
    >
      {initials(name)}
    </span>
  );
}
