import type { AxiosInstance, AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";

type MockAxiosClient = AxiosInstance & ReturnType<typeof vi.fn> & {
  get: ReturnType<typeof vi.fn>;
  defaults: { baseURL?: string };
};

function createClient(response: AxiosResponse = { data: { ok: true }, status: 200, statusText: "OK", headers: {}, config: {} }) {
  const client = vi.fn().mockResolvedValue(response) as unknown as MockAxiosClient;
  client.defaults = { baseURL: "http://localhost:8080/api/v1" };
  client.get = vi.fn().mockResolvedValue({ data: {}, status: 200 });
  return client;
}

function forbiddenError(url: string, method = "patch", headers: Record<string, string> = {}) {
  return {
    isAxiosError: true,
    message: "Forbidden",
    response: { status: 403, data: { code: "FORBIDDEN", message: "Forbidden" } },
    config: { url, method, headers },
  };
}

async function importCsrfRetry() {
  vi.resetModules();
  return import("@/shared/api/interceptors/csrf-retry");
}

describe("tryHandleCsrfError", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  it("bootstraps CSRF and retries an unsafe request that was sent without a CSRF header", async () => {
    const { tryHandleCsrfError } = await importCsrfRetry();
    const response = { data: { saved: true }, status: 200, statusText: "OK", headers: {}, config: {} };
    const client = createClient(response);

    const result = await tryHandleCsrfError(forbiddenError("/auth/me/password"), client);

    expect(result).toEqual({ handled: true, response });
    expect(client.get).toHaveBeenCalledWith("/auth/csrf", {
      skipAuthRefresh: true,
      skipCsrfBootstrap: true,
    });
    expect(client).toHaveBeenCalledWith(expect.objectContaining({
      url: "/auth/me/password",
      method: "patch",
      hasRetriedWithCsrf: true,
    }));
  });

  it("does not retry a forbidden unsafe request that already had a CSRF header", async () => {
    const { tryHandleCsrfError } = await importCsrfRetry();
    const client = createClient();

    const result = await tryHandleCsrfError(
      forbiddenError("/auth/me/password", "patch", { "X-XSRF-TOKEN": "csrf-token" }),
      client,
    );

    expect(result).toEqual({ handled: false });
    expect(client.get).not.toHaveBeenCalled();
    expect(client).not.toHaveBeenCalled();
  });
});
