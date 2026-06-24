import type { InternalAxiosRequestConfig } from "axios";
import { describe, expect, it } from "vitest";
import { attachRequestIdHeader } from "@/shared/api/interceptors/request-id-header";

function config(): InternalAxiosRequestConfig {
  return { headers: {} } as InternalAxiosRequestConfig;
}

describe("attachRequestIdHeader", () => {
  it("sets a non-empty request id", () => {
    const request = config();

    attachRequestIdHeader(request);

    expect(request.headers["X-Request-ID"]).toEqual(expect.any(String));
    expect(String(request.headers["X-Request-ID"]).length).toBeGreaterThan(0);
  });

  it("generates distinct ids across requests", () => {
    const first = config();
    const second = config();

    attachRequestIdHeader(first);
    attachRequestIdHeader(second);

    expect(first.headers["X-Request-ID"]).not.toBe(second.headers["X-Request-ID"]);
  });
});
