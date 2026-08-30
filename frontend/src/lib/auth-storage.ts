import type { UserSummary } from "@/types/api";

// localStorage, not an httpOnly cookie: this is a pure client-side SPA
// talking directly to a separate backend API (no Next.js server acting as a
// backend-for-frontend that could set an httpOnly cookie on the same
// origin), so there's no session for the Next.js server itself to hold.
// Trade-off documented in docs/security.md: a token in localStorage is
// readable by any script running on the page, i.e. vulnerable to theft via
// XSS. Mitigated by a short token lifetime (1 hour, see JWT_EXPIRATION_MINUTES)
// and by the fact this is a demo/assessment app, not handling real user data.

const TOKEN_KEY = "ffp.accessToken";
const USER_KEY = "ffp.user";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): UserSummary | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserSummary;
  } catch {
    return null;
  }
}

export function storeSession(token: string, user: UserSummary): void {
  window.localStorage.setItem(TOKEN_KEY, token);
  window.localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
}
