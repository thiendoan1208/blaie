import axios, { type AxiosInstance } from "axios";
import { normalizeError } from "../errors/normalize-error";
import { ensureCsrfCookie } from "./csrf-header";

declare module "axios" {
  export interface AxiosRequestConfig {
    hasRetriedWithCsrf?: boolean;
  }

  export interface InternalAxiosRequestConfig {
    hasRetriedWithCsrf?: boolean;
  }
}

const AUTH_CSRF_PATH = "/auth/csrf";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const UNSAFE_METHODS = new Set(["post", "put", "patch", "delete"]);

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

function requestHasCsrfHeader(error: unknown) {
  if (!axios.isAxiosError(error) || !error.config?.headers) {
    return false;
  }

  const headers = error.config.headers;
  if (typeof headers.get === "function") {
    return Boolean(headers.get(CSRF_HEADER_NAME));
  }

  const rawHeaders = headers as Record<string, unknown>;
  return Boolean(rawHeaders[CSRF_HEADER_NAME] ?? rawHeaders[CSRF_HEADER_NAME.toLowerCase()]);
}

function shouldAttemptCsrfRetry(error: unknown, baseURL: string | undefined) {
  if (!axios.isAxiosError(error) || error.response?.status !== 403 || !error.config) {
    return false;
  }

  if (error.config.skipAuthRefresh || error.config.hasRetriedWithCsrf || requestHasCsrfHeader(error)) {
    return false;
  }

  const method = error.config.method?.toLowerCase();
  if (!method || !UNSAFE_METHODS.has(method)) {
    return false;
  }

  const pathname = requestPathname(error.config.url, baseURL);
  return pathname ? pathname !== AUTH_CSRF_PATH : true;
}

export async function tryHandleCsrfError(
  error: unknown,
  client: AxiosInstance,
) {
  if (!shouldAttemptCsrfRetry(error, client.defaults.baseURL)) {
    return { handled: false };
  }

  if (!axios.isAxiosError(error) || !error.config) {
    return { handled: false };
  }

  error.config.hasRetriedWithCsrf = true;

  try {
    await ensureCsrfCookie(client, true);
    return { handled: true, response: await client(error.config) };
  } catch (csrfError) {
    return Promise.reject(normalizeError(csrfError));
  }
}
