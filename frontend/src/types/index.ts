// Shapes of the JSON the SevaSync Java API returns. Kept in one place so a backend change is a one-file update.

export type Role = 'ADMIN' | 'DONOR' | 'RECIPIENT' | 'VOLUNTEER';
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED';
export type DonorType = 'HOSTEL_MESS' | 'CANTEEN' | 'EVENT_COORDINATOR' | 'CAFE' | 'CONFERENCE';
export type RecipientType = 'ORPHANAGE' | 'OLD_AGE_HOME' | 'SHELTER' | 'COMMUNITY_KITCHEN' | 'NGO';
export type NeedLevel = 'NORMAL' | 'HIGH' | 'CRITICAL';
export type VehicleType = 'WALK' | 'BICYCLE' | 'SCOOTER' | 'CAR';
export type FoodCategory = 'COOKED_MEAL' | 'BEVERAGE' | 'BAKERY' | 'FRUITS_VEGETABLES' | 'PACKAGED';
export type BatchStatus = 'AVAILABLE' | 'ALLOCATED' | 'COMPLETED' | 'EXPIRED' | 'WITHDRAWN';
export type DispatchStatus = 'ASSIGNED' | 'CLAIMED' | 'PICKED_UP' | 'DELIVERED' | 'CANCELLED';
export type DispatchAction = 'claim' | 'release' | 'pickup' | 'deliver' | 'confirm' | 'cancel';

export type Permission =
  | 'VIEW_NETWORK' | 'UPDATE_OWN_PROFILE' | 'LOG_BATCH' | 'WITHDRAW_BATCH' | 'VIEW_OWN_BATCHES'
  | 'VIEW_OPEN_PICKUPS' | 'CLAIM_DISPATCH' | 'PROGRESS_DISPATCH' | 'VIEW_INCOMING' | 'CONFIRM_RECEIPT'
  | 'VIEW_ALL_BATCHES' | 'VIEW_ALL_DISPATCHES' | 'RUN_MATCHING' | 'ASSIGN_MANUALLY' | 'CANCEL_ANY_DISPATCH'
  | 'MANAGE_USERS' | 'VIEW_AUDIT' | 'VIEW_REPORTS' | 'RUN_BENCHMARK' | 'MANAGE_SETTINGS' | 'MANAGE_BACKUPS';

export interface LatLng {
  lat: number;
  lng: number;
}

/** What other participants may see about someone. */
export interface PublicUser {
  id: string;
  role: Role;
  name: string;
  location?: LatLng;
  donorType?: DonorType;
  recipientType?: RecipientType;
  needLevel?: NeedLevel;
  dailyCapacity?: number;
  vehicle?: VehicleType;
}

/** The signed-in user's own profile. */
export interface User extends PublicUser {
  email: string;
  phone: string;
  address: string;
  status: AccountStatus;
  createdAt: string;
  permissions: Permission[];
}

export interface Batch {
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
  notes: string;
  createdAt: string;
  donor?: PublicUser | null;
  minutesLeft: number;
  freshness: number;
  kg: number;
  dispatches?: Dispatch[];
  history?: AuditEntry[];
}

export interface Dispatch {
  id: string;
  batchId: string;
  recipientId: string;
  servings: number;
  status: DispatchStatus;
  strategy: string;
  distanceKm: number;
  assignedAt: string;
  volunteerId: string | null;
  claimedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
  confirmedAt: string | null;
  rating: number | null;
  feedback: string;
  note: string;
  allowedActions: DispatchAction[];
  batch?: Batch;
  donor?: PublicUser | null;
  recipient?: PublicUser | null;
  volunteer?: PublicUser | null;
  distanceToPickupKm?: number;
  tripKm?: number;
  etaMinutes?: number;
  fitsVehicle?: boolean;
  history?: AuditEntry[];
}

export interface NetworkDonor extends PublicUser {
  openBatches: number;
  servingsAvailable: number;
}

export interface NetworkRecipient extends PublicUser {
  remainingToday: number;
}

export interface NetworkVolunteer extends PublicUser {
  activePickups: number;
}

export interface NetworkBatch {
  id: string;
  donorId: string;
  foodName: string;
  category: FoodCategory;
  vegetarian: boolean;
  remainingServings: number;
  servings: number;
  status: BatchStatus;
  minutesLeft: number;
  freshness: number;
}

export interface NetworkRoute {
  id: string;
  batchId: string;
  foodName: string;
  donorId: string;
  recipientId: string;
  volunteerId: string | null;
  status: DispatchStatus;
  servings: number;
  distanceKm: number;
  assignedAt: string;
  claimedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
}

export interface Network {
  generatedAt: string;
  center: LatLng;
  donors: NetworkDonor[];
  recipients: NetworkRecipient[];
  volunteers: NetworkVolunteer[];
  batches: NetworkBatch[];
  routes: NetworkRoute[];
  recentDeliveries: NetworkRoute[];
}

export interface AuditEntry {
  seq: number;
  timestamp: string;
  actorId: string;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  prevHash: string;
  hash: string;
}

export interface AuditVerification {
  valid: boolean;
  entries: number;
  firstBadSeq: number | null;
  reason: string;
}

export interface MatchResult {
  committed: boolean;
  strategy: string;
  allocatedServings: number;
  unallocatedServings: number;
  batchesConsidered: number;
  recipientsAvailable: number;
  averageDistanceKm: number;
  dispatches: Dispatch[];
}

export interface StrategyInfo {
  id: string;
  name: string;
  description: string;
}

export interface MatchingSettings {
  activeStrategy: string;
  autoMatch: boolean;
  strategies: StrategyInfo[];
}

export interface BenchmarkRow {
  strategy: string;
  name: string;
  servingsOffered: number;
  servingsDelivered: number;
  servingsWasted: number;
  deliveredPercent: number;
  wastedPercent: number;
  averageDistanceKm: number;
  averageDeliveryMinutes: number;
  urgentSharePercent: number;
  fairnessIndex: number;
  wasteCauses: { noRecipientRoom: number; waitingForVolunteer: number; unreachableInTime: number };
}

export interface BenchmarkReport {
  runs: number;
  seed: number;
  scenario: { donors: number; recipients: number; volunteers: number; batchesPerDay: number };
  results: BenchmarkRow[];
  lowestWaste: string;
  elapsedMillis: number;
}

export interface RankEntry {
  id: string;
  name: string;
  servings: number;
  distanceKm?: number;
}

export interface TimelinePoint {
  date: string;
  donated: number;
  delivered: number;
  wasted: number;
}

export interface ImpactReport {
  generatedAt: string;
  batches: number;
  servingsDonated: number;
  servingsDelivered: number;
  servingsInTransit: number;
  servingsAvailable: number;
  servingsWasted: number;
  deliveries: number;
  deliveryRatePercent: number;
  foodSavedKg: number;
  foodWastedKg: number;
  co2AvoidedKg: number;
  averageDeliveryMinutes: number;
  donors: number;
  recipients: number;
  volunteers: number;
  pendingVerifications: number;
  topDonors: RankEntry[];
  topRecipients: RankEntry[];
  topVolunteers: RankEntry[];
  timeline: TimelinePoint[];
  categories: { category: FoodCategory; donated: number; delivered: number }[];
  strategies: { strategy: string; dispatches: number; servings: number; averageDistanceKm: number }[];
}

export interface PublicStats {
  mealsServed: number;
  foodSavedKg: number;
  co2AvoidedKg: number;
  deliveries: number;
  donors: number;
  recipients: number;
  volunteers: number;
  deliveryRatePercent: number;
}

/** Personal impact; which fields exist depends on the role. */
export type PersonalImpact = Partial<ImpactReport> & {
  role: Role;
  averageRating?: number | null;
  servingsReceived?: number;
  deliveriesReceived?: number;
  servingsIncoming?: number;
  foodReceivedKg?: number;
  awaitingConfirmation?: number;
  distanceKm?: number;
  averageTripMinutes?: number;
  activePickups?: number;
  mealsServed?: number;
  vehicle?: VehicleType;
};

export interface Meta {
  roles: { id: Role; name: string; needsVerification: boolean }[];
  categories: { id: FoodCategory; maxShelfLifeMinutes: number; kgPerServing: number }[];
  vehicles: { id: VehicleType; speedKmh: number; maxServings: number }[];
  donorTypes: DonorType[];
  recipientTypes: RecipientType[];
  needLevels: NeedLevel[];
  minRemainingMinutes: number;
  maxServings: number;
}

export interface AuthResult {
  token: string;
  expiresAt: string;
  user: User;
}

export interface LiveEvent {
  id: number;
  type: string;
  entityType: string;
  entityId: string;
  message: string;
  data: Record<string, unknown>;
  at: string;
}

export interface RegistrationForm {
  role: Exclude<Role, 'ADMIN'>;
  name: string;
  email: string;
  password: string;
  phone: string;
  address?: string;
  lat?: number;
  lng?: number;
  donorType?: DonorType;
  recipientType?: RecipientType;
  needLevel?: NeedLevel;
  dailyCapacity?: number;
  vehicle?: VehicleType;
}
