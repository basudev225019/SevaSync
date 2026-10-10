import React from 'react';
import { 
  Sparkles, 
  Layers, 
  Cpu, 
  Send, 
  Users, 
  BarChart3, 
  ShieldCheck, 
  RefreshCw
} from 'lucide-react';

interface NavbarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  backendOnline: boolean;
  onLoadDemo: () => void;
  loadingDemo: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeTab,
  setActiveTab,
  backendOnline,
  onLoadDemo,
  loadingDemo,
}) => {
  const navItems = [
    { id: 'dashboard', label: 'Overview', icon: Layers },
    { id: 'batches', label: 'Food Batches', icon: Sparkles },
    { id: 'matching', label: 'Smart Matching', icon: Cpu },
    { id: 'dispatches', label: 'Dispatches', icon: Send },
    { id: 'partners', label: 'Donors & Recipients', icon: Users },
    { id: 'impact', label: 'Impact Report', icon: BarChart3 },
    { id: 'audit', label: 'Audit Trail', icon: ShieldCheck },
  ];

  return (
    <header style={{
      position: 'sticky',
      top: 0,
      zIndex: 100,
      background: 'rgba(9, 13, 22, 0.82)',
      backdropFilter: 'blur(16px)',
      WebkitBackdropFilter: 'blur(16px)',
      borderBottom: '1px solid var(--border-subtle)',
    }}>
      <div style={{
        maxWidth: '1380px',
        margin: '0 auto',
        padding: '0.875rem 1.5rem',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '1rem',
      }}>
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer' }}
             onClick={() => setActiveTab('dashboard')}>
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '12px',
            background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 20px rgba(16, 185, 129, 0.35)',
          }}>
            <Sparkles size={22} color="#ffffff" />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em', color: '#ffffff' }}>
                SevaSync
              </span>
              <span className="badge badge-emerald" style={{ fontSize: '0.65rem', padding: '0.15rem 0.45rem' }}>
                v1.0 • SQLite
              </span>
            </div>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Campus Food Redistribution & Priority Engine
            </p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.25rem',
          background: 'rgba(15, 23, 42, 0.6)',
          padding: '0.25rem',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--border-subtle)',
          overflowX: 'auto',
          maxWidth: '100%',
        }}>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '0.5rem 0.875rem',
                  fontSize: '0.8125rem',
                  fontWeight: isActive ? 600 : 500,
                  color: isActive ? '#ffffff' : 'var(--text-secondary)',
                  background: isActive ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
                  border: isActive ? '1px solid rgba(255, 255, 255, 0.12)' : '1px solid transparent',
                  borderRadius: 'var(--radius-md)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  whiteSpace: 'nowrap',
                }}
              >
                <Icon size={16} color={isActive ? '#34d399' : 'currentColor'} />
                {item.label}
              </button>
            );
          })}
        </nav>

        {/* Right Status Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.375rem 0.75rem',
            background: backendOnline ? 'rgba(16, 185, 129, 0.08)' : 'rgba(244, 63, 94, 0.08)',
            border: `1px solid ${backendOnline ? 'rgba(16, 185, 129, 0.25)' : 'rgba(244, 63, 94, 0.25)'}`,
            borderRadius: 'var(--radius-full)',
            fontSize: '0.75rem',
            fontWeight: 600,
            color: backendOnline ? '#34d399' : '#fb7185',
          }}>
            <span className="pulse-dot" style={{ backgroundColor: backendOnline ? 'var(--emerald)' : 'var(--rose)' }} />
            {backendOnline ? 'Backend Online (SQLite)' : 'Backend Offline'}
          </div>

          <button
            onClick={onLoadDemo}
            disabled={loadingDemo}
            className="btn btn-secondary btn-sm"
            title="Seed sample hostel mess and shelter data"
          >
            <RefreshCw size={14} className={loadingDemo ? 'spin' : ''} />
            {loadingDemo ? 'Loading...' : 'Demo Data'}
          </button>
        </div>
      </div>
    </header>
  );
};
