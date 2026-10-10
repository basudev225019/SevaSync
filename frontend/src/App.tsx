import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { Dashboard } from './pages/Dashboard';
import { BatchesView } from './pages/BatchesView';
import { MatchingView } from './pages/MatchingView';
import { DispatchesView } from './pages/DispatchesView';
import { PartnersView } from './pages/PartnersView';
import { ImpactView } from './pages/ImpactView';
import { AuditView } from './pages/AuditView';

import { CreateBatchModal } from './components/modals/CreateBatchModal';
import { CreateDonorModal } from './components/modals/CreateDonorModal';
import { CreateRecipientModal } from './components/modals/CreateRecipientModal';
import { DeliverModal } from './components/modals/DeliverModal';
import { CancelModal } from './components/modals/CancelModal';

import { api } from './services/api';
import { 
  AuditEntry, 
  AuditVerification, 
  Dispatch, 
  Donor, 
  FoodBatch, 
  ImpactReport, 
  MatchResult, 
  Recipient 
} from './types';
import { AlertCircle, CheckCircle2 } from 'lucide-react';

export function App() {
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [backendOnline, setBackendOnline] = useState<boolean>(false);
  const [loadingDemo, setLoadingDemo] = useState<boolean>(false);

  // Core Data State
  const [donors, setDonors] = useState<Donor[]>([]);
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [batches, setBatches] = useState<FoodBatch[]>([]);
  const [dispatches, setDispatches] = useState<Dispatch[]>([]);
  const [report, setReport] = useState<ImpactReport | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditEntry[]>([]);
  const [verification, setVerification] = useState<AuditVerification | null>(null);
  const [loadingVerify, setLoadingVerify] = useState<boolean>(false);

  // Modals
  const [isCreateBatchOpen, setIsCreateBatchOpen] = useState(false);
  const [isCreateDonorOpen, setIsCreateDonorOpen] = useState(false);
  const [isCreateRecipientOpen, setIsCreateRecipientOpen] = useState(false);
  const [deliverTarget, setDeliverTarget] = useState<Dispatch | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Dispatch | null>(null);

  // Feedback Toast
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  // Fetch all data
  const refreshData = useCallback(async () => {
    try {
      const health = await api.getHealth();
      setBackendOnline(health.status === 'UP');

      const [dList, rList, bList, dspList, repData, audList] = await Promise.all([
        api.getDonors().catch(() => []),
        api.getRecipients().catch(() => []),
        api.getBatches().catch(() => []),
        api.getDispatches().catch(() => []),
        api.getImpactReport().catch(() => null),
        api.getAuditLogs(100).catch(() => []),
      ]);

      setDonors(dList);
      setRecipients(rList);
      setBatches(bList);
      setDispatches(dspList);
      setReport(repData);
      setAuditLogs(audList);
    } catch {
      setBackendOnline(false);
    }
  }, []);

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 10000);
    return () => clearInterval(interval);
  }, [refreshData]);

  // Actions
  const handleLoadDemo = async () => {
    try {
      setLoadingDemo(true);
      const res = await api.loadDemoData();
      showToast(res.message, res.loaded ? 'success' : 'error');
      await refreshData();
    } catch (err: any) {
      showToast(err.message || 'Failed to load demo data', 'error');
    } finally {
      setLoadingDemo(false);
    }
  };

  const handleCreateBatch = async (data: any) => {
    await api.createBatch(data);
    showToast('Surplus food batch logged and verified!');
    await refreshData();
  };

  const handleCreateDonor = async (data: any) => {
    await api.createDonor(data);
    showToast('Donor registered successfully!');
    await refreshData();
  };

  const handleCreateRecipient = async (data: any) => {
    await api.createRecipient(data);
    showToast('Recipient registered successfully!');
    await refreshData();
  };

  const handleDeliverDispatch = async (dispatchId: string, volunteer: string) => {
    await api.deliverDispatch(dispatchId, volunteer);
    showToast(`Dispatch ${dispatchId} delivered by ${volunteer}!`);
    await refreshData();
  };

  const handleCancelDispatch = async (dispatchId: string, reason: string) => {
    await api.cancelDispatch(dispatchId, reason);
    showToast(`Dispatch ${dispatchId} cancelled. Servings returned.`);
    await refreshData();
  };

  const handlePreviewMatching = async (): Promise<MatchResult> => {
    const res = await api.previewMatching();
    showToast(`Simulated: ${res.allocatedServings} servings can be allocated across ${res.dispatches.length} dispatches.`);
    return res;
  };

  const handleRunMatching = async (): Promise<MatchResult> => {
    const res = await api.runMatching();
    showToast(`Smart Matching executed! Generated ${res.dispatches.length} new dispatches.`);
    await refreshData();
    return res;
  };

  const handleVerifyAudit = async () => {
    try {
      setLoadingVerify(true);
      const res = await api.verifyAuditTrail();
      setVerification(res);
      if (res.valid) {
        showToast(`Audit Chain Verified: All ${res.entries} cryptographic blocks are valid!`);
      } else {
        showToast(`Tampering Detected in audit log at seq #${res.firstBadSeq}!`, 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Verification failed', 'error');
    } finally {
      setLoadingVerify(false);
    }
  };

  return (
    <div className="app-container">
      {/* Toast Notification */}
      {toast && (
        <div
          className="fade-in"
          style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            padding: '0.875rem 1.25rem',
            borderRadius: 'var(--radius-md)',
            background: toast.type === 'success' ? '#064e3b' : '#881337',
            border: `1px solid ${toast.type === 'success' ? '#10b981' : '#f43f5e'}`,
            color: '#ffffff',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.5)',
            fontSize: '0.875rem',
            fontWeight: 600,
          }}
        >
          {toast.type === 'success' ? <CheckCircle2 size={18} color="#34d399" /> : <AlertCircle size={18} color="#fb7185" />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Main Navbar */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        backendOnline={backendOnline}
        onLoadDemo={handleLoadDemo}
        loadingDemo={loadingDemo}
      />

      {/* Content Area */}
      <main className="main-content">
        {activeTab === 'dashboard' && (
          <Dashboard
            report={report}
            batches={batches}
            dispatches={dispatches}
            onOpenCreateBatch={() => setIsCreateBatchOpen(true)}
            onOpenCreateRecipient={() => setIsCreateRecipientOpen(true)}
            onRunMatching={() => setActiveTab('matching')}
            onNavigateTab={(tab) => setActiveTab(tab)}
          />
        )}

        {activeTab === 'batches' && (
          <BatchesView
            batches={batches}
            onOpenCreateBatch={() => setIsCreateBatchOpen(true)}
          />
        )}

        {activeTab === 'matching' && (
          <MatchingView
            batches={batches}
            recipients={recipients}
            onPreviewMatching={handlePreviewMatching}
            onRunMatching={handleRunMatching}
          />
        )}

        {activeTab === 'dispatches' && (
          <DispatchesView
            dispatches={dispatches}
            onOpenDeliverModal={(d) => setDeliverTarget(d)}
            onOpenCancelModal={(d) => setCancelTarget(d)}
          />
        )}

        {activeTab === 'partners' && (
          <PartnersView
            donors={donors}
            recipients={recipients}
            onOpenCreateDonor={() => setIsCreateDonorOpen(true)}
            onOpenCreateRecipient={() => setIsCreateRecipientOpen(true)}
          />
        )}

        {activeTab === 'impact' && (
          <ImpactView report={report} />
        )}

        {activeTab === 'audit' && (
          <AuditView
            logs={auditLogs}
            verification={verification}
            onVerify={handleVerifyAudit}
            loadingVerify={loadingVerify}
          />
        )}
      </main>

      {/* Modals */}
      <CreateBatchModal
        isOpen={isCreateBatchOpen}
        onClose={() => setIsCreateBatchOpen(false)}
        donors={donors}
        onSubmit={handleCreateBatch}
      />

      <CreateDonorModal
        isOpen={isCreateDonorOpen}
        onClose={() => setIsCreateDonorOpen(false)}
        onSubmit={handleCreateDonor}
      />

      <CreateRecipientModal
        isOpen={isCreateRecipientOpen}
        onClose={() => setIsCreateRecipientOpen(false)}
        onSubmit={handleCreateRecipient}
      />

      <DeliverModal
        isOpen={!!deliverTarget}
        onClose={() => setDeliverTarget(null)}
        dispatch={deliverTarget}
        onConfirm={handleDeliverDispatch}
      />

      <CancelModal
        isOpen={!!cancelTarget}
        onClose={() => setCancelTarget(null)}
        dispatch={cancelTarget}
        onConfirm={handleCancelDispatch}
      />
    </div>
  );
}

export default App;
