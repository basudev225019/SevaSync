import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, Outlet, useLocation, useRouteError } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { LiveProvider } from './context/LiveContext';
import { AppShell } from './components/layout/AppShell';
import { Button } from './components/ui/Button';
import type { Role } from './types';

// every page is its own chunk; the 3D and chart libraries load only when a page needs them
const Landing = lazy(() => import('./pages/Landing'));
const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const DonateView = lazy(() => import('./pages/DonateView'));
const BatchesView = lazy(() => import('./pages/BatchesView'));
const DispatchesView = lazy(() => import('./pages/DispatchesView'));
const PickupsView = lazy(() => import('./pages/PickupsView'));
const NetworkView = lazy(() => import('./pages/NetworkView'));
const MatchingView = lazy(() => import('./pages/MatchingView'));
const PartnersView = lazy(() => import('./pages/PartnersView'));
const ImpactView = lazy(() => import('./pages/ImpactView'));
const AuditView = lazy(() => import('./pages/AuditView'));
const ProfileView = lazy(() => import('./pages/ProfileView'));
const NotFound = lazy(() => import('./pages/NotFound'));

function PageLoader() {
  return (
    <div className="grid min-h-[60dvh] place-items-center">
      <Loader2 className="size-6 animate-spin text-saffron-300" />
    </div>
  );
}

const page = (el: ReactNode) => <Suspense fallback={<PageLoader />}>{el}</Suspense>;

/** Signed-in area: waits for the stored session to be checked, then shows the shell with live updates. */
function RequireAuth() {
  const { user, booting } = useAuth();
  const location = useLocation();
  if (booting) return <PageLoader />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return (
    <LiveProvider>
      <AppShell />
    </LiveProvider>
  );
}

/** Page visible only to some roles, and only once the account is verified. */
function Allow({ roles, active = true, children }: { roles: Role[]; active?: boolean; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role) || (active && user.status !== 'ACTIVE')) return <Navigate to="/app" replace />;
  return <>{children}</>;
}

/** Public pages redirect signed-in users straight to their dashboard. */
function GuestOnly() {
  const { user, booting } = useAuth();
  if (booting) return <PageLoader />;
  return user ? <Navigate to="/app" replace /> : <Outlet />;
}

function RouteError() {
  const error = useRouteError();
  return (
    <div className="grid min-h-dvh place-items-center bg-night-900 px-6 text-center">
      <div className="max-w-md">
        <h1 className="text-2xl font-semibold">Something broke on this page</h1>
        <p className="mt-2 text-sm text-ink-400">{error instanceof Error ? error.message : 'Unexpected error'}</p>
        <Button className="mt-6" onClick={() => window.location.assign('/app')}>Reload</Button>
      </div>
    </div>
  );
}

const ALL: Role[] = ['ADMIN', 'DONOR', 'RECIPIENT', 'VOLUNTEER'];

const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      { path: '/', element: page(<Landing />) },
      {
        element: <GuestOnly />,
        children: [
          { path: '/login', element: page(<Login />) },
          { path: '/register', element: page(<Register />) },
        ],
      },
      {
        path: '/app',
        element: <RequireAuth />,
        children: [
          { index: true, element: page(<Dashboard />) },
          { path: 'donate', element: page(<Allow roles={['DONOR']}><DonateView /></Allow>) },
          { path: 'batches', element: page(<Allow roles={['DONOR', 'ADMIN']}><BatchesView /></Allow>) },
          { path: 'dispatches', element: page(<Allow roles={['DONOR', 'RECIPIENT', 'ADMIN']}><DispatchesView /></Allow>) },
          { path: 'pickups', element: page(<Allow roles={['VOLUNTEER']}><PickupsView /></Allow>) },
          { path: 'network', element: page(<Allow roles={ALL}><NetworkView /></Allow>) },
          { path: 'matching', element: page(<Allow roles={['ADMIN']}><MatchingView /></Allow>) },
          { path: 'partners', element: page(<Allow roles={['ADMIN']}><PartnersView /></Allow>) },
          { path: 'impact', element: page(<Allow roles={['ADMIN']}><ImpactView /></Allow>) },
          { path: 'audit', element: page(<Allow roles={['ADMIN']}><AuditView /></Allow>) },
          { path: 'profile', element: page(<Allow roles={ALL} active={false}><ProfileView /></Allow>) },
        ],
      },
      { path: '*', element: page(<NotFound />) },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
