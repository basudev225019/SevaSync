import type { ReactNode } from 'react';
import {
  Activity,
  Bike,
  BrainCircuit,
  ClipboardList,
  Globe2,
  HandPlatter,
  LayoutDashboard,
  PackagePlus,
  ShieldCheck,
  Truck,
  UserRound,
  Users,
  UtensilsCrossed,
} from 'lucide-react';
import type { Role } from '../types';

export interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  /** Shown only to verified (ACTIVE) accounts. */
  activeOnly?: boolean;
}

const i = (Icon: typeof Activity) => <Icon className="size-[18px]" strokeWidth={1.8} />;

/** Each role gets its own navigation, so nobody sees a menu entry they cannot use. */
export const NAV: Record<Role, NavItem[]> = {
  DONOR: [
    { to: '/app', label: 'Home', icon: i(LayoutDashboard) },
    { to: '/app/donate', label: 'Log surplus', icon: i(PackagePlus), activeOnly: true },
    { to: '/app/batches', label: 'My food', icon: i(UtensilsCrossed), activeOnly: true },
    { to: '/app/dispatches', label: 'Deliveries', icon: i(Truck), activeOnly: true },
    { to: '/app/network', label: 'Live map', icon: i(Globe2), activeOnly: true },
    { to: '/app/profile', label: 'Profile', icon: i(UserRound) },
  ],
  VOLUNTEER: [
    { to: '/app', label: 'Home', icon: i(LayoutDashboard) },
    { to: '/app/pickups', label: 'Pickups', icon: i(Bike), activeOnly: true },
    { to: '/app/network', label: 'Live map', icon: i(Globe2), activeOnly: true },
    { to: '/app/profile', label: 'Profile', icon: i(UserRound) },
  ],
  RECIPIENT: [
    { to: '/app', label: 'Home', icon: i(LayoutDashboard) },
    { to: '/app/dispatches', label: 'Deliveries', icon: i(HandPlatter), activeOnly: true },
    { to: '/app/network', label: 'Live map', icon: i(Globe2), activeOnly: true },
    { to: '/app/profile', label: 'Profile', icon: i(UserRound) },
  ],
  ADMIN: [
    { to: '/app', label: 'Command centre', icon: i(LayoutDashboard) },
    { to: '/app/network', label: 'Live map', icon: i(Globe2) },
    { to: '/app/matching', label: 'Matching lab', icon: i(BrainCircuit) },
    { to: '/app/batches', label: 'Food', icon: i(UtensilsCrossed) },
    { to: '/app/dispatches', label: 'Dispatches', icon: i(ClipboardList) },
    { to: '/app/partners', label: 'Partners', icon: i(Users) },
    { to: '/app/impact', label: 'Impact', icon: i(Activity) },
    { to: '/app/audit', label: 'Audit trail', icon: i(ShieldCheck) },
    { to: '/app/profile', label: 'Profile', icon: i(UserRound) },
  ],
};
