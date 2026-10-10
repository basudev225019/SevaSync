import React, { useState } from 'react';
import { 
  ShieldCheck, 
  ShieldAlert, 
  RefreshCw, 
  Lock, 
  Search, 
  Fingerprint 
} from 'lucide-react';
import { AuditEntry, AuditVerification } from '../types';

interface AuditViewProps {
  logs: AuditEntry[];
  verification: AuditVerification | null;
  onVerify: () => Promise<void>;
  loadingVerify: boolean;
}

export const AuditView: React.FC<AuditViewProps> = ({
  logs,
  verification,
  onVerify,
  loadingVerify,
}) => {
  const [searchTerm, setSearchTerm] = useState('');

  const filteredLogs = logs.filter((e) =>
    e.action.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.entityType.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.entityId.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.details.toLowerCase().includes(searchTerm.toLowerCase()) ||
    e.hash.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Header & Verification Card */}
      <div
        className="glass-panel"
        style={{
          padding: '2rem',
          background: 'radial-gradient(ellipse at top left, rgba(16, 185, 129, 0.12) 0%, rgba(15, 23, 42, 0.8) 70%)',
          border: '1px solid var(--border-subtle)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1.5rem',
        }}
      >
        <div style={{ maxWidth: '640px' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span className="badge badge-emerald">
              <Lock size={12} />
              SHA-256 Merkle-Style Hash Chaining
            </span>
            <span className="badge badge-indigo">
              Append-Only Tamper-Evident Trail
            </span>
          </div>
          <h2 style={{ fontSize: '1.875rem', fontWeight: 800, color: '#ffffff', letterSpacing: '-0.02em', marginBottom: '0.5rem' }}>
            Cryptographic Audit Verification
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            Every donor intake, batch creation, match assignment, and delivery hand-over is cryptographically hashed with its preceding block hash. Any unauthorized modification or row tampering immediately breaks the chain.
          </p>
        </div>

        {/* Verification Status & Button */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.75rem' }}>
          <button
            onClick={onVerify}
            disabled={loadingVerify}
            className="btn btn-primary"
          >
            <RefreshCw size={16} className={loadingVerify ? 'spin' : ''} />
            {loadingVerify ? 'Verifying Hashes...' : 'Verify Cryptographic Integrity'}
          </button>

          {verification && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.5rem 0.875rem',
              borderRadius: 'var(--radius-md)',
              background: verification.valid ? 'rgba(16, 185, 129, 0.15)' : 'rgba(244, 63, 94, 0.15)',
              border: `1px solid ${verification.valid ? 'rgba(16, 185, 129, 0.3)' : 'rgba(244, 63, 94, 0.3)'}`,
              color: verification.valid ? '#34d399' : '#fb7185',
              fontSize: '0.8125rem',
              fontWeight: 600,
            }}>
              {verification.valid ? <ShieldCheck size={18} /> : <ShieldAlert size={18} />}
              <span>
                {verification.valid
                  ? `Chain Verified: All ${verification.entries} entries valid`
                  : `Tampering Detected at Seq #${verification.firstBadSeq}!`}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Search Filter */}
      <div className="glass-panel" style={{ padding: '1rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <Search size={18} color="var(--text-muted)" />
        <input
          type="text"
          className="input-field"
          placeholder="Search by action, entity ID, hash substring, details..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ border: 'none', background: 'transparent', padding: '0.25rem' }}
        />
      </div>

      {/* Audit Log Table */}
      <div className="table-container glass-panel">
        <table className="custom-table">
          <thead>
            <tr>
              <th style={{ width: '60px' }}>Seq</th>
              <th>Timestamp</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Details</th>
              <th>SHA-256 Hash</th>
            </tr>
          </thead>
          <tbody>
            {filteredLogs.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                  No audit log entries recorded.
                </td>
              </tr>
            ) : (
              filteredLogs.map((entry) => (
                <tr key={entry.seq}>
                  <td style={{ fontWeight: 700, color: '#ffffff' }}>#{entry.seq}</td>
                  <td style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                    {entry.timestamp.replace('T', ' ')}
                  </td>
                  <td>
                    <span className="badge badge-emerald" style={{ fontSize: '0.65rem' }}>
                      {entry.action}
                    </span>
                  </td>
                  <td>
                    <div style={{ fontSize: '0.8125rem' }}>
                      <span style={{ color: 'var(--text-secondary)' }}>{entry.entityType}: </span>
                      <span style={{ fontWeight: 600, color: '#ffffff' }}>{entry.entityId}</span>
                    </div>
                  </td>
                  <td style={{ fontSize: '0.8125rem', color: 'var(--text-primary)', maxWidth: '380px' }}>
                    {entry.details}
                  </td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                      <Fingerprint size={14} color="var(--indigo)" />
                      <span className="font-mono" style={{ color: 'var(--text-muted)' }} title={entry.hash}>
                        {entry.hash.substring(0, 16)}...
                      </span>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
