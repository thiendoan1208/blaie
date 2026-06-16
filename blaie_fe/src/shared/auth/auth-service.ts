import { clearAuthSession, getAccessToken, readAuthSession, writeAuthSession } from "./auth-storage";
import type { AuthSession } from "./auth-types";

export function loadAuthSession(): AuthSession | null {
  return readAuthSession();
}

export function saveAuthSession(session: AuthSession): void {
  writeAuthSession(session);
}

export function removeAuthSession(): void {
  clearAuthSession();
}

export function loadAccessToken(): string | null {
  return getAccessToken();
}
