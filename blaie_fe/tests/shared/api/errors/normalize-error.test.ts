import axios from "axios";
import { describe, expect, it } from "vitest";
import { createAppError } from "@/shared/api/errors/app-error";
import { normalizeError } from "@/shared/api/errors/normalize-error";

function axiosError(overrides: Partial<Parameters<typeof normalizeError>[0]> & Record<string, unknown>) {
  return {
    isAxiosError: true,
    message: "Request failed",
    ...overrides,
  };
}

describe("normalizeError", () => {
  it("returns existing AppError instances unchanged", () => {
    const appError = createAppError({ code: "FORBIDDEN", status: 403, message: "Forbidden" });

    expect(normalizeError(appError)).toBe(appError);
  });

  it("maps axios timeout errors", () => {
    const error = normalizeError(axiosError({ code: "ECONNABORTED" }));

    expect(error.code).toBe("TIMEOUT");
    expect(error.status).toBe(0);
  });

  it("preserves backend error bodies", () => {
    const error = normalizeError(
      axiosError({
        response: {
          status: 422,
          headers: { "x-request-id": "request-1" },
          data: {
            code: "VALIDATION_ERROR",
            message: "Validation failed",
            errors: { email: ["Email is invalid"] },
            requestId: "request-body",
          },
        },
      }),
    );

    expect(error.code).toBe("VALIDATION_ERROR");
    expect(error.status).toBe(422);
    expect(error.fieldErrors).toEqual({ email: ["Email is invalid"] });
    expect(error.requestId).toBe("request-body");
  });

  it.each([
    [400, "BAD_REQUEST"],
    [401, "UNAUTHORIZED"],
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
    [422, "VALIDATION_ERROR"],
  ])("falls back status %s to %s", (status, code) => {
    const error = normalizeError(
      axiosError({
        response: {
          status,
          headers: {},
          data: { message: "Fallback message" },
        },
      }),
    );

    expect(error.code).toBe(code);
    expect(error.message).toBe("Fallback message");
  });

  it("maps network and unknown errors", () => {
    expect(normalizeError(axiosError({ response: undefined })).code).toBe("NETWORK_ERROR");
    expect(normalizeError(new Error("boom")).code).toBe("UNKNOWN_ERROR");
  });

  it("can normalize real AxiosError objects", () => {
    const raw = new axios.AxiosError("No response");
    const error = normalizeError(raw);

    expect(error.code).toBe("NETWORK_ERROR");
  });
});
