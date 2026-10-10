export type DonorType = 'HOSTEL_MESS' | 'CANTEEN' | 'CATERER' | 'EVENT_COORDINATOR' | 'OTHER';

export type RecipientType = 'ORPHANAGE' | 'OLD_AGE_HOME' | 'SHELTER' | 'COMMUNITY_KITCHEN' | 'NGO' | 'SLUM_SCHOOL';

export type NeedLevel = 'CRITICAL' | 'HIGH' | 'NORMAL';

export type FoodCategory = 'COOKED_MEAL' | 'BEVERAGE' | 'BAKERY' | 'FRUITS_VEGETABLES' | 'PACKAGED';

export type BatchStatus = 'AVAILABLE' | 'ALLOCATED' | 'COMPLETED' | 'EXPIRED';

export type DispatchStatus = 'ASSIGNED' | 'DELIVERED' | 'CANCELLED';

export interface Donor {
  id: string;
  name: string;
  phone: string;
  email: string;
  address: string;
  donorType: DonorType;
  createdAt: string;
}

export interface Recipient {
  id: string;
  name: string;
  phone: string;
  email: string;
  address: string;
  recipientType: RecipientType;
  needLevel: NeedLevel;
  dailyCapacity: number;
  remainingCapacityToday?: number;
  createdAt: string;
}

export interface FoodBatch {
  id: string;
  donorId: string;
  foodName: string;
  category: FoodCategory;
  vegetarian: boolean;
  servings: number;
  remainingServings: number;
  preparedAt: string;
  shelfLifeMinutes: number;
  expiresAt: string;
  status: BatchStatus;
  createdAt: string;
}

export interface Dispatch {
  id: string;
  batchId: string;
  recipientId: string;
  servings: number;
  status: DispatchStatus;
  assignedAt: string;
  deliveredAt: string | null;
  volunteer: string;
  note: string;
}

export interface RankEntry {
  id: string;
  name: string;
  servings: number;
}

export interface ImpactReport {
  generatedAt: string;
  donors: number;
  recipients: number;
  batches: number;
  servingsDonated: number;
  servingsDelivered: number;
  servingsInTransit: number;
  servingsAvailable: number;
  servingsWasted: number;
  deliveryRatePercent: number;
  foodSavedKg: number;
  co2AvoidedKg: number;
  recipientsServed: number;
  topDonors: RankEntry[];
  topRecipients: RankEntry[];
}

export interface AuditEntry {
  seq: number;
  timestamp: string;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  hash: string;
}

export interface AuditVerification {
  valid: boolean;
  entries: number;
  firstBadSeq: number;
}

export interface MatchResult {
  committed: boolean;
  batchesConsidered: number;
  recipientsAvailable: number;
  allocatedServings: number;
  unallocatedServings: number;
  dispatches: Dispatch[];
}
