import axios, { type AxiosInstance } from "axios";
import { isProtectedRoutePath, routePaths } from "@/shared/routes/route-paths";
import { normalizeError } from "../errors/normalize-error";

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
const AUTH_REFRESH_SKIP_PATHS = new Set([
  "/auth/csrf",
  "/auth/login",
  "/auth/logout",
  "/auth/register",
  AUTH_REFRESH_PATH,
]);

let refreshSessionPromise: Promise<void> | null = null;

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

function refreshAccessToken(client: AxiosInstance) {
  refreshSessionPromise ??= client
    .post(AUTH_REFRESH_PATH, undefined, { skipAuthRefresh: true })
    .then(() => undefined)
    .finally(() => {
      refreshSessionPromise = null;
    });

  return refreshSessionPromise;
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
    redirectToLoginIfProtectedRoute();
    return Promise.reject(normalizeError(refreshError));
  }
}
