import { describe, expect, it } from "vitest";

import { shouldDiscardCaptureSubmission } from "@/features/inbox/model/inbox-errors";
import { createAppError } from "@/shared/api/errors/app-error";

function appError(status: number) {
  return createAppError({
    code: `HTTP_${status}`,
    status,
    message: "request failed",
  });
}

describe("Inbox submission error policy", () => {
  it.each([400, 401, 403, 404, 409, 422])(
    "discards a key after deterministic HTTP %s rejection",
    (status) => {
      expect(shouldDiscardCaptureSubmission(appError(status))).toBe(true);
    },
  );

  it.each([0, 408, 425, 429, 500, 503])(
    "retains a key after ambiguous or retryable HTTP %s failure",
    (status) => {
      expect(shouldDiscardCaptureSubmission(appError(status))).toBe(false);
    },
  );

  it("retains a key for an unknown client error", () => {
    expect(shouldDiscardCaptureSubmission(new Error("network failed"))).toBe(false);
  });
});
