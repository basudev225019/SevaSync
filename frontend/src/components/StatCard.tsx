import React from 'react';
import { LucideIcon } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  variant?: 'emerald' | 'amber' | 'indigo' | 'cyan' | 'rose';
  trend?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  variant = 'emerald',
  trend,
}) => {
  const colorMap = {
    emerald: {
      accent: '#10b981',
      glow: 'rgba(16, 185, 129, 0.15)',
      border: 'rgba(16, 185, 129, 0.25)',
      bg: 'rgba(16, 185, 129, 0.1)',
    },
    amber: {
      accent: '#f59e0b',
      glow: 'rgba(245, 158, 11, 0.15)',
      border: 'rgba(245, 158, 11, 0.25)',
      bg: 'rgba(245, 158, 11, 0.1)',
    },
    indigo: {
      accent: '#6366f1',
      glow: 'rgba(99, 102, 241, 0.15)',
      border: 'rgba(99, 102, 241, 0.25)',
      bg: 'rgba(99, 102, 241, 0.1)',
    },
    cyan: {
      accent: '#06b6d4',
      glow: 'rgba(6, 182, 212, 0.15)',
      border: 'rgba(6, 182, 212, 0.25)',
      bg: 'rgba(6, 182, 212, 0.1)',
    },
    rose: {
      accent: '#f43f5e',
      glow: 'rgba(244, 63, 94, 0.15)',
      border: 'rgba(244, 63, 94, 0.25)',
      bg: 'rgba(244, 63, 94, 0.1)',
    },
  };

  const current = colorMap[variant];

  return (
    <div
      className="glass-panel"
      style={{
        padding: '1.25rem 1.5rem',
        position: 'relative',
        overflow: 'hidden',
        background: `linear-gradient(135deg, rgba(15, 23, 42, 0.8) 0%, rgba(15, 23, 42, 0.5) 100%)`,
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: '-20px',
          right: '-20px',
          width: '80px',
          height: '80px',
          borderRadius: '50%',
          background: current.glow,
          filter: 'blur(30px)',
          pointerEvents: 'none',
        }}
      />

      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
        <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {title}
        </span>
        <div
          style={{
            width: '36px',
            height: '36px',
            borderRadius: '10px',
            background: current.bg,
            border: `1px solid ${current.border}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon size={18} color={current.accent} />
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.625rem' }}>
        <span style={{ fontSize: '1.875rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.03em', lineHeight: 1 }}>
          {value}
        </span>
        {trend && (
          <span style={{ fontSize: '0.75rem', fontWeight: 600, color: current.accent }}>
            {trend}
          </span>
        )}
      </div>

      {subtitle && (
        <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.5rem' }}>
          {subtitle}
        </p>
      )}
    </div>
  );
};
