import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";
import { readBrowserCookie } from "../utils/browser-cookie";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const AUTH_CSRF_PATH = "/auth/csrf";
const UNSAFE_METHODS = new Set(["post", "put", "patch", "delete"]);
let csrfBootstrapPromise: Promise<void> | null = null;

declare module "axios" {
  export interface AxiosRequestConfig {
    skipCsrfBootstrap?: boolean;
  }

  export interface InternalAxiosRequestConfig {
    skipCsrfBootstrap?: boolean;
  }
}

function headerValue(
  config: InternalAxiosRequestConfig,
  name: string,
): unknown {
  if (typeof config.headers.get === "function") {
    return config.headers.get(name);
  }
  const headers = config.headers as unknown as Record<string, unknown>;
  return headers[name] ?? headers[name.toLowerCase()];
}

function usesBearerAuthentication(config: InternalAxiosRequestConfig): boolean {
  const authorization = headerValue(config, "Authorization");
  return (
    typeof authorization === "string" &&
    authorization.trim().toLowerCase().startsWith("bearer ")
  );
}

function isUnsafeRequest(config: InternalAxiosRequestConfig): boolean {
  const method = config.method?.toLowerCase();
  return Boolean(method && UNSAFE_METHODS.has(method));
}

export function ensureCsrfCookie(
  client: AxiosInstance,
  force = false,
): Promise<void> {
  if (typeof document === "undefined") return Promise.resolve();
  if (!force && readBrowserCookie(CSRF_COOKIE_NAME)) return Promise.resolve();

  csrfBootstrapPromise ??= client
    .get(AUTH_CSRF_PATH, {
      skipAuthRefresh: true,
      skipCsrfBootstrap: true,
    })
    .then(() => undefined)
    .finally(() => {
      csrfBootstrapPromise = null;
    });
  return csrfBootstrapPromise;
}

export function attachCsrfHeader(config: InternalAxiosRequestConfig) {
  const method = config.method?.toLowerCase();
  const csrfToken = method && UNSAFE_METHODS.has(method) ? readBrowserCookie(CSRF_COOKIE_NAME) : undefined;

  if (csrfToken) {
    Object.assign(config.headers, {
      [CSRF_HEADER_NAME]: csrfToken,
    });
  }
}

export async function prepareCsrfRequest(
  config: InternalAxiosRequestConfig,
  client: AxiosInstance,
) {
  if (
    isUnsafeRequest(config) &&
    !config.skipCsrfBootstrap &&
    !usesBearerAuthentication(config) &&
    !readBrowserCookie(CSRF_COOKIE_NAME)
  ) {
    await ensureCsrfCookie(client);
  }

  attachCsrfHeader(config);
  return config;
}
