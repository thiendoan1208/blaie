import type { AxiosInstance, AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";

type MockAxiosClient = AxiosInstance & ReturnType<typeof vi.fn> & {
  post: ReturnType<typeof vi.fn>;
  get: ReturnType<typeof vi.fn>;
  defaults: { baseURL?: string };
};

function createClient(response: AxiosResponse = { data: { ok: true }, status: 200, statusText: "OK", headers: {}, config: {} }) {
  const client = vi.fn().mockResolvedValue(response) as unknown as MockAxiosClient;
  client.defaults = { baseURL: "http://localhost:8080/api/v1" };
  client.post = vi.fn().mockResolvedValue({ data: {}, status: 200 });
  client.get = vi.fn().mockResolvedValue({ data: {}, status: 200 });
  return client;
}

function unauthorizedError(url: string) {
  return {
    isAxiosError: true,
    message: "Unauthorized",
    response: { status: 401, data: { code: "UNAUTHORIZED", message: "Unauthorized" } },
    config: { url, method: "get", headers: {} },
  };
}

async function importAuthRefresh() {
  vi.resetModules();
  return import("@/shared/api/interceptors/auth-refresh");
}

describe("tryHandleAuthRefreshError", () => {
  beforeEach(() => {
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
    history.pushState({}, "", "/");
  });

  it("refreshes and retries the original request after a 401", async () => {
    const { tryHandleAuthRefreshError } = await importAuthRefresh();
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const response = { data: { retried: true }, status: 200, statusText: "OK", headers: {}, config: {} };
    const client = createClient(response);

    const result = await tryHandleAuthRefreshError(unauthorizedError("/items"), client);

    expect(result).toEqual({ handled: true, response });
    expect(client.post).toHaveBeenCalledWith("/auth/refresh", undefined, { skipAuthRefresh: true });
    expect(client).toHaveBeenCalledWith(expect.objectContaining({ url: "/items" }));
  });

  it("bootstraps CSRF before refreshing when the CSRF cookie is missing", async () => {
    const { tryHandleAuthRefreshError } = await importAuthRefresh();
    const client = createClient();

    await tryHandleAuthRefreshError(unauthorizedError("/items"), client);

    expect(client.get).toHaveBeenCalledWith("/auth/csrf", { skipAuthRefresh: true });
    expect(client.post).toHaveBeenCalledWith("/auth/refresh", undefined, { skipAuthRefresh: true });
  });

  it.each(["/auth/csrf", "/auth/login", "/auth/logout", "/auth/register", "/auth/refresh"])(
    "does not refresh for %s",
    async (url) => {
      const { tryHandleAuthRefreshError } = await importAuthRefresh();
      const client = createClient();

      const result = await tryHandleAuthRefreshError(unauthorizedError(url), client);

      expect(result).toEqual({ handled: false });
      expect(client.post).not.toHaveBeenCalled();
      expect(client).not.toHaveBeenCalled();
    },
  );

  it("shares one refresh request across same-tab 401s", async () => {
    const { tryHandleAuthRefreshError } = await importAuthRefresh();
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const client = createClient();
    let finishRefresh!: () => void;
    client.post.mockReturnValue(new Promise<void>((resolve) => {
      finishRefresh = resolve;
    }));

    const first = tryHandleAuthRefreshError(unauthorizedError("/items/1"), client);
    const second = tryHandleAuthRefreshError(unauthorizedError("/items/2"), client);

    await vi.waitFor(() => expect(client.post).toHaveBeenCalledTimes(1));
    finishRefresh();

    await expect(first).resolves.toMatchObject({ handled: true });
    await expect(second).resolves.toMatchObject({ handled: true });
    expect(client).toHaveBeenCalledTimes(2);
  });

  it("rejects normalized errors when refresh fails", async () => {
    const { tryHandleAuthRefreshError } = await importAuthRefresh();
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const client = createClient();
    client.post.mockRejectedValue({
      isAxiosError: true,
      message: "Refresh failed",
      response: { status: 401, data: { code: "UNAUTHORIZED", message: "Unauthorized" } },
    });

    await expect(tryHandleAuthRefreshError(unauthorizedError("/items"), client)).rejects.toMatchObject({
      code: "UNAUTHORIZED",
      status: 401,
    });
  });

  it("waits for another tab refresh result before retrying", async () => {
    const { tryHandleAuthRefreshError } = await importAuthRefresh();
    const client = createClient();
    localStorage.setItem(
      "blaie.auth.refresh.lock",
      JSON.stringify({ ownerId: "other-tab", expiresAt: Date.now() + 5000 }),
    );

    const resultPromise = tryHandleAuthRefreshError(unauthorizedError("/items"), client);
    window.setTimeout(() => {
      localStorage.setItem(
        "blaie.auth.refresh.result",
        JSON.stringify({ ownerId: "other-tab", status: "success", createdAt: Date.now() }),
      );
    }, 20);

    await expect(resultPromise).resolves.toMatchObject({ handled: true });
    expect(client.post).not.toHaveBeenCalled();
    expect(client).toHaveBeenCalledWith(expect.objectContaining({ url: "/items" }));
  });
});
