import {
  AuditEntry,
  AuditVerification,
  BatchStatus,
  Dispatch,
  DispatchStatus,
  Donor,
  FoodBatch,
  ImpactReport,
  MatchResult,
  Recipient,
} from '../types';

const API_BASE = '/api';

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let errMessage = `Request failed (${res.status})`;
    try {
      const err = await res.json();
      if (err.error) {
        errMessage = `${err.error}${err.type ? ` (${err.type})` : ''}`;
      }
    } catch {
      // ignore
    }
    throw new Error(errMessage);
  }
  return res.json();
}

export const api = {
  // Health
  async getHealth(): Promise<{ status: string; time: string }> {
    const res = await fetch(`${API_BASE}/health`);
    return handleResponse(res);
  },

  // Donors
  async getDonors(): Promise<Donor[]> {
    const res = await fetch(`${API_BASE}/donors`);
    return handleResponse(res);
  },

  async createDonor(data: {
    name: string;
    phone: string;
    email?: string;
    address?: string;
    donorType: string;
  }): Promise<Donor> {
    const res = await fetch(`${API_BASE}/donors`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  // Recipients
  async getRecipients(): Promise<Recipient[]> {
    const res = await fetch(`${API_BASE}/recipients`);
    return handleResponse(res);
  },

  async createRecipient(data: {
    name: string;
    phone: string;
    email?: string;
    address?: string;
    recipientType: string;
    needLevel: string;
    dailyCapacity: number;
  }): Promise<Recipient> {
    const res = await fetch(`${API_BASE}/recipients`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  // Food Batches
  async getBatches(status?: BatchStatus): Promise<FoodBatch[]> {
    const url = status ? `${API_BASE}/batches?status=${status}` : `${API_BASE}/batches`;
    const res = await fetch(url);
    return handleResponse(res);
  },

  async createBatch(data: {
    donorId: string;
    foodName: string;
    category: string;
    vegetarian: boolean;
    servings: number;
    shelfLifeHours: number;
    preparedAt?: string;
  }): Promise<FoodBatch> {
    const res = await fetch(`${API_BASE}/batches`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  // Dispatches
  async getDispatches(status?: DispatchStatus): Promise<Dispatch[]> {
    const url = status ? `${API_BASE}/dispatches?status=${status}` : `${API_BASE}/dispatches`;
    const res = await fetch(url);
    return handleResponse(res);
  },

  async createManualDispatch(data: {
    batchId: string;
    recipientId: string;
    servings: number;
  }): Promise<Dispatch> {
    const res = await fetch(`${API_BASE}/dispatches`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  async deliverDispatch(id: string, volunteer: string): Promise<Dispatch> {
    const res = await fetch(`${API_BASE}/dispatches/${id}/deliver`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ volunteer }),
    });
    return handleResponse(res);
  },

  async cancelDispatch(id: string, reason: string): Promise<Dispatch> {
    const res = await fetch(`${API_BASE}/dispatches/${id}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    return handleResponse(res);
  },

  // Matching
  async previewMatching(): Promise<MatchResult> {
    const res = await fetch(`${API_BASE}/matching/preview`, {
      method: 'POST',
    });
    return handleResponse(res);
  },

  async runMatching(): Promise<MatchResult> {
    const res = await fetch(`${API_BASE}/matching/run`, {
      method: 'POST',
    });
    return handleResponse(res);
  },

  // Impact Reports
  async getImpactReport(): Promise<ImpactReport> {
    const res = await fetch(`${API_BASE}/reports/impact`);
    return handleResponse(res);
  },

  // Audit
  async getAuditLogs(limit: number = 50): Promise<AuditEntry[]> {
    const res = await fetch(`${API_BASE}/audit?limit=${limit}`);
    return handleResponse(res);
  },

  async verifyAuditTrail(): Promise<AuditVerification> {
    const res = await fetch(`${API_BASE}/audit/verify`);
    return handleResponse(res);
  },

  // Demo Data Seeder
  async loadDemoData(): Promise<{ loaded: boolean; message: string }> {
    const res = await fetch(`${API_BASE}/demo`, {
      method: 'POST',
    });
    return handleResponse(res);
  },
};
