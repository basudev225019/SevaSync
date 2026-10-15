import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, getToken, setToken, setUnauthorizedHandler } from '../services/api';
import type { Permission, RegistrationForm, User } from '../types';

interface AuthState {
  user: User | null;
  token: string | null;
  /** True until the stored session has been checked against the server on first load. */
  booting: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (form: RegistrationForm) => Promise<User>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  can: (permission: Permission) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [token, setTokenState] = useState<string | null>(getToken());
  const [booting, setBooting] = useState<boolean>(!!getToken());

  const clear = useCallback(() => {
    setToken(null);
    setTokenState(null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(clear);
  }, [clear]);

  useEffect(() => {
    if (!getToken()) return;
    let cancelled = false;
    api
      .me()
      .then((r) => !cancelled && setUser(r.user))
      .catch(() => !cancelled && clear())
      .finally(() => !cancelled && setBooting(false));
    return () => {
      cancelled = true;
    };
  }, [clear]);

  const login = useCallback(async (email: string, password: string) => {
    const r = await api.login(email, password);
    setToken(r.token);
    setTokenState(r.token);
    setUser(r.user);
    return r.user;
  }, []);

  const register = useCallback(async (form: RegistrationForm) => {
    const r = await api.register(form);
    setToken(r.token);
    setTokenState(r.token);
    setUser(r.user);
    return r.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // already signed out server-side
    }
    clear();
  }, [clear]);

  const refresh = useCallback(async () => {
    const r = await api.me();
    setUser(r.user);
  }, []);

  const can = useCallback((p: Permission) => !!user?.permissions.includes(p), [user]);

  const value = useMemo(
    () => ({ user, token, booting, login, register, logout, refresh, can }),
    [user, token, booting, login, register, logout, refresh, can],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// the hook lives next to its provider on purpose; fast refresh simply reloads this one file
// oxlint-disable-next-line react/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
