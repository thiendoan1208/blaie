import type { InternalAxiosRequestConfig } from "axios";
import { readBrowserCookie } from "../utils/browser-cookie";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const UNSAFE_METHODS = new Set(["post", "put", "patch", "delete"]);

export function attachCsrfHeader(config: InternalAxiosRequestConfig) {
  const method = config.method?.toLowerCase();
  const csrfToken = method && UNSAFE_METHODS.has(method) ? readBrowserCookie(CSRF_COOKIE_NAME) : undefined;

  if (csrfToken) {
    Object.assign(config.headers, {
      [CSRF_HEADER_NAME]: csrfToken,
    });
  }
}
