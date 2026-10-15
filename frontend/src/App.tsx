import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, Outlet, useLocation, useRouteError } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { LiveProvider } from './context/LiveContext';
import { AppShell } from './components/layout/AppShell';
import { Button } from './components/ui/Button';
import type { Role } from './types';

const RELOAD_FLAG = 'sevasync.reloaded-for-update';

/** True for the error a browser throws when an old tab asks for a page file that a new build replaced. */
function isStaleBuildError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(message);
}

/**
 * lazy() that survives a redeploy: page files carry content hashes, so a tab opened before a rebuild asks for
 * files that no longer exist. Reload once to pick up the new version; a second failure is shown as a real error.
 */
function lazyPage<T extends ComponentType<object>>(load: () => Promise<{ default: T }>) {
  return lazy(() =>
    load().then(
      (module) => {
        sessionStorage.removeItem(RELOAD_FLAG);
        return module;
      },
      (error: unknown) => {
        if (isStaleBuildError(error) && !sessionStorage.getItem(RELOAD_FLAG)) {
          sessionStorage.setItem(RELOAD_FLAG, '1');
          window.location.reload();
          return new Promise<{ default: T }>(() => undefined); // keep showing the loader until the reload happens
        }
        throw error;
      },
    ),
  );
}

// every page is its own chunk; the 3D and chart libraries load only when a page needs them
const Landing = lazyPage(() => import('./pages/Landing'));
const Login = lazyPage(() => import('./pages/Login'));
const Register = lazyPage(() => import('./pages/Register'));
const Dashboard = lazyPage(() => import('./pages/Dashboard'));
const DonateView = lazyPage(() => import('./pages/DonateView'));
const BatchesView = lazyPage(() => import('./pages/BatchesView'));
const DispatchesView = lazyPage(() => import('./pages/DispatchesView'));
const PickupsView = lazyPage(() => import('./pages/PickupsView'));
const NetworkView = lazyPage(() => import('./pages/NetworkView'));
const MatchingView = lazyPage(() => import('./pages/MatchingView'));
const PartnersView = lazyPage(() => import('./pages/PartnersView'));
const ImpactView = lazyPage(() => import('./pages/ImpactView'));
const AuditView = lazyPage(() => import('./pages/AuditView'));
const ProfileView = lazyPage(() => import('./pages/ProfileView'));
const NotFound = lazyPage(() => import('./pages/NotFound'));

function PageLoader() {
  return (
    <div className="grid min-h-[60dvh] place-items-center">
      <Loader2 className="size-6 animate-spin text-saffron-600" />
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
  const stale = isStaleBuildError(error);
  return (
    <div className="grid min-h-dvh place-items-center bg-night-900 px-6 text-center">
      <div className="max-w-md">
        <h1 className="text-2xl font-semibold">{stale ? 'SevaSync was updated' : 'Something broke on this page'}</h1>
        <p className="mt-2 text-sm text-ink-400">
          {stale
            ? 'A newer version is available. Reload to continue where you were.'
            : error instanceof Error ? error.message : 'Unexpected error'}
        </p>
        <Button className="mt-6" onClick={() => (stale ? window.location.reload() : window.location.assign('/app'))}>Reload</Button>
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
