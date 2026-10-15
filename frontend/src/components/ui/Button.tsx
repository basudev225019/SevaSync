import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { clsx } from 'clsx';
import { Loader2 } from 'lucide-react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
type Size = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
  trailing?: ReactNode;
}

const variants: Record<Variant, string> = {
  primary:
    'bg-gradient-to-b from-saffron-300 to-saffron-500 text-night-950 shadow-[0_8px_30px_-10px_rgba(251,191,36,0.7)] hover:brightness-110',
  secondary: 'glass text-ink-100 hover:bg-white/8',
  ghost: 'text-ink-300 hover:text-ink-100 hover:bg-white/5',
  danger: 'bg-rose-500/15 text-rose-200 border border-rose-400/25 hover:bg-rose-500/25',
  success: 'bg-leaf-500/15 text-leaf-300 border border-leaf-400/25 hover:bg-leaf-500/25',
};

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-xl',
  lg: 'h-12 px-6 text-[15px] gap-2.5 rounded-2xl',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, icon, trailing, className, children, disabled, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      className={clsx(
        'inline-flex select-none items-center justify-center whitespace-nowrap font-semibold transition-all duration-200 active:scale-[0.97] disabled:pointer-events-none disabled:opacity-45',
        variants[variant],
        sizes[size],
        className,
      )}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Loader2 className="size-4 animate-spin" /> : icon}
      {children}
      {trailing}
    </button>
  );
});
