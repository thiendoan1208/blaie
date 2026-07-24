import axios, { type AxiosInstance } from "axios";
import { isProtectedRoutePath, routePaths } from "@/shared/routes/route-paths";
import { normalizeError } from "../errors/normalize-error";
import { ensureCsrfCookie } from "./csrf-header";

declare module "axios" {
  export interface AxiosRequestConfig {
    skipAuthRefresh?: boolean;
    hasRetriedWithRefresh?: boolean;
  }

  export interface InternalAxiosRequestConfig {
    skipAuthRefresh?: boolean;
    hasRetriedWithRefresh?: boolean;
  }
}

const AUTH_REFRESH_PATH = "/auth/refresh";
const AUTH_CSRF_PATH = "/auth/csrf";
const AUTH_LOGOUT_PATH = "/auth/logout";
const AUTH_REFRESH_SKIP_PATHS = new Set([
  AUTH_CSRF_PATH,
  "/auth/login",
  AUTH_LOGOUT_PATH,
  "/auth/register",
  AUTH_REFRESH_PATH,
]);
const REFRESH_LOCK_KEY = "blaie.auth.refresh.lock";
const REFRESH_RESULT_KEY = "blaie.auth.refresh.result";
const REFRESH_CHANNEL_NAME = "blaie.auth.refresh";
const REFRESH_LOCK_TTL_MS = 10000;
const REFRESH_WAIT_TIMEOUT_MS = 12000;
const REFRESH_WAIT_POLL_MS = 100;

let refreshSessionPromise: Promise<void> | null = null;
let tabOwnerId: string | null = null;

type RefreshLock = {
  ownerId: string;
  expiresAt: number;
};

type RefreshResult = {
  ownerId: string;
  status: "success" | "failure";
  createdAt: number;
};

function requestPathname(url: string | undefined, baseURL: string | undefined): string | undefined {
  if (!url) {
    return undefined;
  }

  try {
    return new URL(url, baseURL).pathname.replace(/^\/api\/v1/, "");
  } catch {
    return undefined;
  }
}

function shouldAttemptRefresh(error: unknown, baseURL: string | undefined) {
  if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
    return false;
  }

  if (error.config.skipAuthRefresh || error.config.hasRetriedWithRefresh) {
    return false;
  }

  const pathname = requestPathname(error.config.url, baseURL);
  return pathname ? !AUTH_REFRESH_SKIP_PATHS.has(pathname) : true;
}

function canUseBrowserCoordination() {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

function getTabOwnerId() {
  tabOwnerId ??= typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `tab-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return tabOwnerId;
}

function parseRefreshLock(value: string | null): RefreshLock | null {
  if (!value) {
    return null;
  }

  try {
    const parsed = JSON.parse(value) as Partial<RefreshLock>;
    return typeof parsed.ownerId === "string" && typeof parsed.expiresAt === "number"
      ? { ownerId: parsed.ownerId, expiresAt: parsed.expiresAt }
      : null;
  } catch {
    return null;
  }
}

function parseRefreshResult(value: string | null): RefreshResult | null {
  if (!value) {
    return null;
  }

  try {
    const parsed = JSON.parse(value) as Partial<RefreshResult>;
    return typeof parsed.ownerId === "string"
      && (parsed.status === "success" || parsed.status === "failure")
      && typeof parsed.createdAt === "number"
      ? { ownerId: parsed.ownerId, status: parsed.status, createdAt: parsed.createdAt }
      : null;
  } catch {
    return null;
  }
}

function tryAcquireRefreshLock(ownerId: string) {
  const now = Date.now();
  const currentLock = parseRefreshLock(window.localStorage.getItem(REFRESH_LOCK_KEY));

  if (currentLock && currentLock.expiresAt > now && currentLock.ownerId !== ownerId) {
    return false;
  }

  window.localStorage.setItem(
    REFRESH_LOCK_KEY,
    JSON.stringify({ ownerId, expiresAt: now + REFRESH_LOCK_TTL_MS } satisfies RefreshLock),
  );

  return parseRefreshLock(window.localStorage.getItem(REFRESH_LOCK_KEY))?.ownerId === ownerId;
}

function releaseRefreshLock(ownerId: string) {
  const currentLock = parseRefreshLock(window.localStorage.getItem(REFRESH_LOCK_KEY));
  if (currentLock?.ownerId === ownerId) {
    window.localStorage.removeItem(REFRESH_LOCK_KEY);
  }
}

function openRefreshChannel() {
  return typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(REFRESH_CHANNEL_NAME);
}

function publishRefreshResult(result: RefreshResult) {
  window.localStorage.setItem(REFRESH_RESULT_KEY, JSON.stringify(result));

  const channel = openRefreshChannel();
  channel?.postMessage(result);
  channel?.close();
}

function waitForOtherTabRefresh(ownerId: string) {
  const startedAt = Date.now();

  return new Promise<"completed" | "lock-available">((resolve, reject) => {
    const channel = openRefreshChannel();

    function cleanup() {
      channel?.close();
      window.clearInterval(pollId);
      window.clearTimeout(timeoutId);
    }

    function settleFromResult(result: RefreshResult) {
      cleanup();
      if (result.status === "success") {
        resolve("completed");
      } else {
        reject(new Error("Session refresh failed in another tab."));
      }
    }

    function inspectRefreshState() {
      const result = parseRefreshResult(window.localStorage.getItem(REFRESH_RESULT_KEY));
      if (result && result.ownerId !== ownerId && result.createdAt >= startedAt) {
        settleFromResult(result);
        return;
      }

      const currentLock = parseRefreshLock(window.localStorage.getItem(REFRESH_LOCK_KEY));
      if (!currentLock || currentLock.expiresAt <= Date.now()) {
        cleanup();
        resolve("lock-available");
      }
    }

    const pollId = window.setInterval(inspectRefreshState, REFRESH_WAIT_POLL_MS);
    const timeoutId = window.setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for session refresh."));
    }, REFRESH_WAIT_TIMEOUT_MS);

    if (channel) {
      channel.onmessage = (event: MessageEvent<RefreshResult>) => {
        const result = event.data;
        if (result.ownerId !== ownerId && result.createdAt >= startedAt) {
          settleFromResult(result);
        }
      };
    }
  });
}

async function postRefreshRequest(client: AxiosInstance) {
  await ensureCsrfCookie(client);
  await client.post(AUTH_REFRESH_PATH, undefined, { skipAuthRefresh: true });
}

async function clearStaleAuthCookies(client: AxiosInstance) {
  try {
    await ensureCsrfCookie(client);
    await client.post(AUTH_LOGOUT_PATH, undefined, { skipAuthRefresh: true });
  } catch {
    // Best-effort cleanup only. The original refresh error remains authoritative.
  }
}

function refreshAccessToken(client: AxiosInstance) {
  refreshSessionPromise ??= coordinateRefreshAccessToken(client)
    .finally(() => {
      refreshSessionPromise = null;
    });

  return refreshSessionPromise;
}

async function coordinateRefreshAccessToken(client: AxiosInstance): Promise<void> {
  if (!canUseBrowserCoordination()) {
    return postRefreshRequest(client);
  }

  const ownerId = getTabOwnerId();
  if (!tryAcquireRefreshLock(ownerId)) {
    const result = await waitForOtherTabRefresh(ownerId);
    return result === "completed" ? undefined : coordinateRefreshAccessToken(client);
  }

  try {
    await postRefreshRequest(client);
    publishRefreshResult({ ownerId, status: "success", createdAt: Date.now() });
  } catch (error) {
    publishRefreshResult({ ownerId, status: "failure", createdAt: Date.now() });
    throw error;
  } finally {
    releaseRefreshLock(ownerId);
  }
}

function redirectToLoginIfProtectedRoute() {
  if (typeof window === "undefined" || !isProtectedRoutePath(window.location.pathname)) {
    return;
  }

  const loginUrl = new URL(routePaths.login, window.location.origin);
  loginUrl.searchParams.set("next", `${window.location.pathname}${window.location.search}`);
  window.location.assign(loginUrl.toString());
}

export async function tryHandleAuthRefreshError(
  error: unknown,
  client: AxiosInstance,
) {
  if (!shouldAttemptRefresh(error, client.defaults.baseURL)) {
    return { handled: false };
  }

  if (!axios.isAxiosError(error) || !error.config) {
    return { handled: false };
  }

  error.config.hasRetriedWithRefresh = true;

  try {
    await refreshAccessToken(client);
    return { handled: true, response: await client(error.config) };
  } catch (refreshError) {
    await clearStaleAuthCookies(client);
    redirectToLoginIfProtectedRoute();
    return Promise.reject(normalizeError(refreshError));
  }
}
