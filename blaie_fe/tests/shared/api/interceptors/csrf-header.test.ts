import type { InternalAxiosRequestConfig } from "axios";
import { beforeEach, describe, expect, it } from "vitest";
import { attachCsrfHeader } from "@/shared/api/interceptors/csrf-header";

function config(method: string): InternalAxiosRequestConfig {
  return { method, headers: {} } as InternalAxiosRequestConfig;
}

describe("attachCsrfHeader", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  it.each(["post", "put", "patch", "delete"])("adds the CSRF header for %s requests", (method) => {
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const request = config(method);

    attachCsrfHeader(request);

    expect(request.headers["X-XSRF-TOKEN"]).toBe("csrf-token");
  });

  it("does not add the CSRF header for safe requests", () => {
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const request = config("get");

    attachCsrfHeader(request);

    expect(request.headers["X-XSRF-TOKEN"]).toBeUndefined();
  });

  it("does not add the CSRF header when the cookie is missing", () => {
    const request = config("post");

    attachCsrfHeader(request);

    expect(request.headers["X-XSRF-TOKEN"]).toBeUndefined();
  });
});
