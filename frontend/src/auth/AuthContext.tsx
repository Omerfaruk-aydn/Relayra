import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { apiGet, apiPost } from "../lib/api";
import type { AuthResponse, UserSummary } from "../lib/api";

interface AuthState {
  user: UserSummary | null;
  accessToken: string | null;
  expiresAt: number | null;
  loading: boolean;
  error: string | null;
  login: (identifier: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<boolean>;
}

const AuthContext = createContext<AuthState | null>(null);

function readStoredSession(): { user: UserSummary; accessToken: string; expiresAt: number } | null {
  try {
    const raw = sessionStorage.getItem("relayra.session");
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as { user: UserSummary; accessToken: string; expiresAt: number };
    if (!parsed.accessToken || !parsed.user || typeof parsed.expiresAt !== "number") {
      return null;
    }
    if (parsed.expiresAt <= Date.now()) {
      sessionStorage.removeItem("relayra.session");
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [stored] = useState(readStoredSession);
  const [user, setUser] = useState<UserSummary | null>(stored?.user ?? null);
  const [accessToken, setAccessToken] = useState<string | null>(stored?.accessToken ?? null);
  const [expiresAt, setExpiresAt] = useState<number | null>(stored?.expiresAt ?? null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user && accessToken && expiresAt) {
      sessionStorage.setItem("relayra.session", JSON.stringify({ user, accessToken, expiresAt }));
    } else {
      sessionStorage.removeItem("relayra.session");
    }
  }, [user, accessToken, expiresAt]);

  const applySession = useCallback((response: AuthResponse) => {
    setUser(response.user);
    setAccessToken(response.accessToken);
    setExpiresAt(Date.now() + response.expiresInSeconds * 1000);
    setError(null);
  }, []);

  const login = useCallback(
    async (identifier: string, password: string) => {
      setLoading(true);
      setError(null);
      try {
        const response = await apiPost<AuthResponse>("/api/v1/auth/login", { identifier, password });
        applySession(response);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Login failed.");
        throw e;
      } finally {
        setLoading(false);
      }
    },
    [applySession],
  );

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      setLoading(true);
      setError(null);
      try {
        const response = await apiPost<AuthResponse>("/api/v1/auth/register", {
          username,
          email,
          password,
        });
        applySession(response);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Registration failed.");
        throw e;
      } finally {
        setLoading(false);
      }
    },
    [applySession],
  );

  const refresh = useCallback(async (): Promise<boolean> => {
    try {
      const res = await fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" });
      if (!res.ok) {
        return false;
      }
      const response = (await res.json()) as AuthResponse;
      applySession(response);
      return true;
    } catch {
      return false;
    }
  }, [applySession]);

  const logout = useCallback(async () => {
    try {
      await fetch("/api/v1/auth/logout", { method: "POST", credentials: "include" });
    } finally {
      setUser(null);
      setAccessToken(null);
      setExpiresAt(null);
    }
  }, []);

  useEffect(() => {
    if (!accessToken || !expiresAt) {
      return;
    }
    const delay = Math.max(0, expiresAt - Date.now() - 60_000);
    const timer = setTimeout(() => {
      void refresh();
    }, delay);
    return () => clearTimeout(timer);
  }, [accessToken, expiresAt, refresh]);

  useEffect(() => {
    if (!accessToken) {
      return;
    }
    let cancelled = false;
    apiGet<UserSummary>("/api/v1/auth/me", accessToken)
      .then((me) => {
        if (!cancelled) {
          setUser(me);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUser(null);
          setAccessToken(null);
          setExpiresAt(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken]);

  const value = useMemo<AuthState>(
    () => ({ user, accessToken, expiresAt, loading, error, login, register, logout, refresh }),
    [user, accessToken, expiresAt, loading, error, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }
  return ctx;
}
