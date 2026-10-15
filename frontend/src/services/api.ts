import type {
  AccountStatus,
  AuditEntry,
  AuditVerification,
  AuthResult,
  Batch,
  BatchStatus,
  BenchmarkReport,
  Dispatch,
  DispatchStatus,
  FoodCategory,
  ImpactReport,
  MatchResult,
  MatchingSettings,
  Meta,
  Network,
  PersonalImpact,
  PublicStats,
  RegistrationForm,
  Role,
  User,
} from '../types';

const TOKEN_KEY = 'sevasync.token';

/** Error thrown for every non-2xx answer, carrying the server's message and machine-readable code. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, message: string, code: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

let token: string | null = readToken();
let onUnauthorized: (() => void) | null = null;

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  return token;
}

export function setToken(value: string | null): void {
  token = value;
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // private mode: keep the token in memory only
  }
}

/** Called once by the auth provider so an expired session signs the user out everywhere. */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the SevaSync server. Is the backend running?', 'OFFLINE');
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    const message = (data && typeof data === 'object' && 'error' in data ? String(data.error) : null)
      ?? `Request failed (${res.status})`;
    const code = data && typeof data === 'object' && 'code' in data ? String(data.code) : 'ERROR';
    if (res.status === 401 && token && path !== '/auth/login') onUnauthorized?.();
    throw new ApiError(res.status, message, code);
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  return p.length ? `?${p.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')}` : '';
};

export interface NewBatchInput {
  foodName: string;
  category: FoodCategory;
  vegetarian: boolean;
  servings: number;
  shelfLifeMinutes: number;
  preparedAt?: string;
  notes?: string;
}

export type ProfileInput = Partial<Pick<User, 'name' | 'phone' | 'address' | 'donorType' | 'needLevel' | 'dailyCapacity' | 'vehicle'>> & {
  lat?: number;
  lng?: number;
};

export const api = {
  // public
  health: () => request<{ status: string; version: string; time: string }>('GET', '/health'),
  meta: () => request<Meta>('GET', '/meta'),
  publicStats: () => request<PublicStats>('GET', '/public/stats'),
  publicNetwork: () => request<Network>('GET', '/public/network'),

  // auth
  login: (email: string, password: string) => request<AuthResult>('POST', '/auth/login', { email, password }),
  register: (form: RegistrationForm) => request<AuthResult>('POST', '/auth/register', form),
  logout: () => request<void>('POST', '/auth/logout', {}),
  me: () => request<{ user: User; impact: PersonalImpact }>('GET', '/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ message: string }>('POST', '/auth/password', { currentPassword, newPassword }),
  updateProfile: (input: ProfileInput) => request<User>('PATCH', '/me', input),
  myImpact: () => request<PersonalImpact>('GET', '/me/impact'),

  // network
  network: () => request<Network>('GET', '/network'),

  // batches
  batches: (status?: BatchStatus) => request<Batch[]>('GET', `/batches${qs({ status })}`),
  batch: (id: string) => request<Batch>('GET', `/batches/${encodeURIComponent(id)}`),
  logBatch: (input: NewBatchInput) => request<Batch>('POST', '/batches', input),
  withdrawBatch: (id: string, reason: string) =>
    request<Batch>('POST', `/batches/${encodeURIComponent(id)}/withdraw`, { reason }),

  // dispatches
  dispatches: (status?: DispatchStatus) => request<Dispatch[]>('GET', `/dispatches${qs({ status })}`),
  openPickups: () => request<Dispatch[]>('GET', '/dispatches/open'),
  dispatch: (id: string) => request<Dispatch>('GET', `/dispatches/${encodeURIComponent(id)}`),
  dispatchAction: (id: string, action: 'claim' | 'release' | 'pickup' | 'deliver') =>
    request<Dispatch>('POST', `/dispatches/${encodeURIComponent(id)}/${action}`, {}),
  confirmDelivery: (id: string, rating: number, feedback: string) =>
    request<Dispatch>('POST', `/dispatches/${encodeURIComponent(id)}/confirm`, { rating, feedback }),
  cancelDispatch: (id: string, reason: string) =>
    request<Dispatch>('POST', `/dispatches/${encodeURIComponent(id)}/cancel`, { reason }),
  assignManually: (batchId: string, recipientId: string, servings: number) =>
    request<Dispatch>('POST', '/dispatches', { batchId, recipientId, servings }),

  // matching (coordinators)
  matchingSettings: () => request<MatchingSettings>('GET', '/matching/settings'),
  updateMatchingSettings: (strategy?: string, autoMatch?: boolean) =>
    request<MatchingSettings>('PUT', '/matching/settings', { strategy, autoMatch }),
  previewMatching: (strategy?: string) => request<MatchResult>('POST', '/matching/preview', { strategy }),
  runMatching: (strategy?: string) => request<MatchResult>('POST', '/matching/run', { strategy }),
  benchmark: (params: { runs?: number; seed?: number; donors?: number; recipients?: number; volunteers?: number; batches?: number }) =>
    request<BenchmarkReport>('POST', '/matching/benchmark', params),

  // users (coordinators)
  users: (role?: Role, status?: AccountStatus) => request<User[]>('GET', `/users${qs({ role, status })}`),
  createUser: (form: RegistrationForm) => request<User>('POST', '/users', form),
  updateUser: (id: string, input: ProfileInput) => request<User>('PATCH', `/users/${encodeURIComponent(id)}`, input),
  setUserStatus: (id: string, status: AccountStatus) =>
    request<User>('POST', `/users/${encodeURIComponent(id)}/status`, { status }),

  // reports, audit, backups
  impact: () => request<ImpactReport>('GET', '/reports/impact'),
  audit: (limit = 150, entityType?: string, entityId?: string) =>
    request<AuditEntry[]>('GET', `/audit${qs({ limit, entityType, entityId })}`),
  verifyAudit: () => request<AuditVerification>('GET', '/audit/verify'),
  backups: () => request<{ file: string; sizeBytes: number }[]>('GET', '/admin/backups'),
  createBackup: () => request<{ file: string }>('POST', '/admin/backups', {}),
  exportCsv: () => request<{ folder: string }>('POST', '/admin/exports', {}),
};
