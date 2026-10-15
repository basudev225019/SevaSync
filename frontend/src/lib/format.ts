import type {
  BatchStatus,
  DispatchStatus,
  DonorType,
  FoodCategory,
  NeedLevel,
  RecipientType,
  Role,
  VehicleType,
} from '../types';

export const roleLabel: Record<Role, string> = {
  ADMIN: 'Coordinator',
  DONOR: 'Donor',
  RECIPIENT: 'Recipient',
  VOLUNTEER: 'Volunteer',
};

export const categoryLabel: Record<FoodCategory, string> = {
  COOKED_MEAL: 'Cooked meal',
  BEVERAGE: 'Beverage',
  BAKERY: 'Bakery',
  FRUITS_VEGETABLES: 'Fruit & veg',
  PACKAGED: 'Packaged',
};

export const categoryEmoji: Record<FoodCategory, string> = {
  COOKED_MEAL: '🍛',
  BEVERAGE: '🥤',
  BAKERY: '🥐',
  FRUITS_VEGETABLES: '🍎',
  PACKAGED: '📦',
};

export const donorTypeLabel: Record<DonorType, string> = {
  HOSTEL_MESS: 'Hostel mess',
  CANTEEN: 'Canteen',
  EVENT_COORDINATOR: 'Event team',
  CAFE: 'Café',
  CONFERENCE: 'Conference',
};

export const recipientTypeLabel: Record<RecipientType, string> = {
  ORPHANAGE: 'Orphanage',
  OLD_AGE_HOME: 'Old-age home',
  SHELTER: 'Shelter',
  COMMUNITY_KITCHEN: 'Community kitchen',
  NGO: 'NGO',
};

export const needLabel: Record<NeedLevel, string> = {
  NORMAL: 'Normal need',
  HIGH: 'High need',
  CRITICAL: 'Critical need',
};

export const vehicleLabel: Record<VehicleType, string> = {
  WALK: 'On foot',
  BICYCLE: 'Bicycle',
  SCOOTER: 'Scooter',
  CAR: 'Car',
};

export const batchStatusLabel: Record<BatchStatus, string> = {
  AVAILABLE: 'Waiting for a match',
  ALLOCATED: 'Fully matched',
  COMPLETED: 'Delivered',
  EXPIRED: 'Expired',
  WITHDRAWN: 'Withdrawn',
};

export const dispatchStatusLabel: Record<DispatchStatus, string> = {
  ASSIGNED: 'Needs a volunteer',
  CLAIMED: 'Volunteer on the way',
  PICKED_UP: 'In transit',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export const strategyLabel: Record<string, string> = {
  smart: 'Smart',
  'expiry-first': 'Expiry first',
  fifo: 'FIFO',
  manual: 'Manual',
};

const nf = new Intl.NumberFormat('en-IN');
const nf1 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 1 });

export const num = (n: number | null | undefined) => (n == null ? '–' : nf.format(n));
export const num1 = (n: number | null | undefined) => (n == null ? '–' : nf1.format(n));

/** "2 h 05 min", "45 min", "expired". */
export function minutesLeft(m: number): string {
  if (m <= 0) return 'expired';
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rest = m % 60;
  return rest === 0 ? `${h} h` : `${h} h ${String(rest).padStart(2, '0')} min`;
}

export function minutesUntil(iso: string, now: number = Date.now()): number {
  return Math.floor((new Date(iso).getTime() - now) / 60_000);
}

export function timeAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '';
  const s = Math.round((now - new Date(iso).getTime()) / 1000);
  if (s < 45) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d === 1 ? 'yesterday' : `${d} days ago`;
}

export function clock(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function shortDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

/** Turns an audit action like DISPATCH_PICKED_UP into "Dispatch picked up". */
export function humanize(action: string): string {
  const s = action.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}
