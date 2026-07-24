import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  attachCsrfHeader,
  prepareCsrfRequest,
} from "@/shared/api/interceptors/csrf-header";

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

  it("bootstraps a missing CSRF cookie before allowing an unsafe cookie-auth request", async () => {
    const client = {
      get: vi.fn().mockImplementation(async () => {
        document.cookie = "XSRF-TOKEN=bootstrapped-token; path=/";
      }),
      defaults: { baseURL: "http://localhost:8080/api/v1" },
    } as unknown as AxiosInstance;
    const request = config("post");
    request.url = "/captures/text";

    await prepareCsrfRequest(request, client);

    expect(client.get).toHaveBeenCalledWith("/auth/csrf", {
      skipAuthRefresh: true,
      skipCsrfBootstrap: true,
    });
    expect(request.headers["X-XSRF-TOKEN"]).toBe("bootstrapped-token");
  });

  it("shares one CSRF bootstrap across concurrent unsafe requests", async () => {
    let finishBootstrap!: () => void;
    const client = {
      get: vi.fn().mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            finishBootstrap = () => {
              document.cookie = "XSRF-TOKEN=shared-token; path=/";
              resolve();
            };
          }),
      ),
      defaults: { baseURL: "http://localhost:8080/api/v1" },
    } as unknown as AxiosInstance;
    const first = config("post");
    first.url = "/captures/text";
    const second = config("delete");
    second.url = "/captures/capture-1";

    const firstPreparation = prepareCsrfRequest(first, client);
    const secondPreparation = prepareCsrfRequest(second, client);
    await vi.waitFor(() => expect(client.get).toHaveBeenCalledTimes(1));
    finishBootstrap();
    await Promise.all([firstPreparation, secondPreparation]);

    expect(first.headers["X-XSRF-TOKEN"]).toBe("shared-token");
    expect(second.headers["X-XSRF-TOKEN"]).toBe("shared-token");
  });

  it("reuses an existing CSRF cookie across consecutive unsafe requests", async () => {
    document.cookie = "XSRF-TOKEN=persistent-token; path=/";
    const client = {
      get: vi.fn(),
      defaults: { baseURL: "http://localhost:8080/api/v1" },
    } as unknown as AxiosInstance;
    const first = config("post");
    first.url = "/captures/text";
    const second = config("delete");
    second.url = "/captures/capture-1";

    await prepareCsrfRequest(first, client);
    await prepareCsrfRequest(second, client);

    expect(client.get).not.toHaveBeenCalled();
    expect(first.headers["X-XSRF-TOKEN"]).toBe("persistent-token");
    expect(second.headers["X-XSRF-TOKEN"]).toBe("persistent-token");
  });

  it("does not bootstrap CSRF for a bearer-authenticated mutation", async () => {
    const client = {
      get: vi.fn(),
      defaults: { baseURL: "http://localhost:8080/api/v1" },
    } as unknown as AxiosInstance;
    const request = config("post");
    request.url = "/captures/text";
    request.headers.Authorization = "Bearer access-token";

    await prepareCsrfRequest(request, client);

    expect(client.get).not.toHaveBeenCalled();
    expect(request.headers["X-XSRF-TOKEN"]).toBeUndefined();
  });
});
