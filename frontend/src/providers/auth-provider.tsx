"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { clearSession, getStoredToken, storeSession } from "@/lib/auth-storage";
import { SESSION_EXPIRED_EVENT } from "@/lib/api-client";
import { getCurrentUser, login as loginRequest } from "@/features/auth/api";
import type { UserSummary } from "@/types/api";

interface AuthContextValue {
  user: UserSummary | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * On mount, a cached token (if any) is re-validated against GET /auth/me
 * rather than trusted blindly — a token that's expired or belongs to a
 * deleted user is discarded immediately instead of producing a broken UI
 * that only fails on the first real API call.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = getStoredToken();
    if (!token) {
      setIsLoading(false);
      return;
    }
    getCurrentUser()
      .then(setUser)
      .catch(() => clearSession())
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    function handleSessionExpired() {
      setUser(null);
      router.push("/login");
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [router]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await loginRequest(email, password);
    storeSession(response.accessToken, response.user);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
    router.push("/login");
  }, [router]);

  return <AuthContext.Provider value={{ user, isLoading, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
