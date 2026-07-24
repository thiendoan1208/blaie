import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import {
  clearAllInboxTracking,
  getOrCreatePendingSubmission,
  hashCaptureText,
  readInboxTrackingState,
  useInboxTracking,
} from "@/features/inbox/model/inbox-tracking";
import type { TextCapture } from "@/features/inbox/types/inbox";

const USER_ID = "user-1";
const CAPTURE_ID = "62faf104-009e-43ea-8610-559f12496dcd";

function capture(
  processingStatus: TextCapture["processingStatus"],
): TextCapture {
  return {
    id: CAPTURE_ID,
    originalText: "Call mom tonight",
    processingStatus,
    failureCode:
      processingStatus === "failed" ? "ai_provider_unavailable" : null,
    canRetry: processingStatus === "failed",
    items: [],
    createdAt: "2026-07-17T10:00:00Z",
    updatedAt: "2026-07-17T10:01:00Z",
  };
}

describe("Inbox capture tracking", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("hashes exactly the backend-trimmed UTF-8 text", async () => {
    await expect(hashCaptureText("  hello  ")).resolves.toBe(
      "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
    );
  });

  it("reuses an unresolved idempotency key for the same normalized text", async () => {
    const first = await getOrCreatePendingSubmission(
      USER_ID,
      "  Call mom tonight ",
      Date.parse("2026-07-17T10:00:00Z"),
    );
    const replay = await getOrCreatePendingSubmission(
      USER_ID,
      "Call mom tonight",
      Date.parse("2026-07-17T10:05:00Z"),
    );

    expect(replay.idempotencyKey).toBe(first.idempotencyKey);
    expect(
      readInboxTrackingState(
        USER_ID,
        Date.parse("2026-07-17T10:05:00Z"),
      ).pendingSubmissions,
    ).toHaveLength(1);
  });

  it("isolates persisted tracking by authenticated user", async () => {
    const firstUser = await getOrCreatePendingSubmission(
      "user-1",
      "Same text",
    );
    const secondUser = await getOrCreatePendingSubmission(
      "user-2",
      "Same text",
    );

    expect(secondUser.idempotencyKey).not.toBe(firstUser.idempotencyKey);
    expect(readInboxTrackingState("user-1").pendingSubmissions).toHaveLength(1);
    expect(readInboxTrackingState("user-2").pendingSubmissions).toHaveLength(1);
  });

  it("clears all private tracking records on logout", async () => {
    await getOrCreatePendingSubmission("user-1", "Private one");
    await getOrCreatePendingSubmission("user-2", "Private two");

    clearAllInboxTracking();

    expect(readInboxTrackingState("user-1").pendingSubmissions).toEqual([]);
    expect(readInboxTrackingState("user-2").pendingSubmissions).toEqual([]);
  });

  it("expires unresolved keys after the backend idempotency TTL", async () => {
    const createdAt = Date.parse("2026-07-17T10:00:00Z");
    const first = await getOrCreatePendingSubmission(
      USER_ID,
      "Same text",
      createdAt,
    );
    const afterTtl = await getOrCreatePendingSubmission(
      USER_ID,
      "Same text",
      createdAt + 24 * 60 * 60 * 1_000 + 1,
    );

    expect(afterTtl.idempotencyKey).not.toBe(first.idempotencyKey);
  });

  it("keeps a terminal capture for restoration but clears its pending key", async () => {
    const { result } = renderHook(() => useInboxTracking(USER_ID));
    let submission!: Awaited<ReturnType<typeof result.current.beginSubmission>>;

    await act(async () => {
      submission = await result.current.beginSubmission("Call mom tonight");
    });
    act(() => result.current.rememberCapture(submission, capture("processing")));

    expect(result.current.state.captureIds).toEqual([CAPTURE_ID]);
    expect(result.current.state.pendingSubmissions[0]?.captureId).toBe(
      CAPTURE_ID,
    );

    act(() => result.current.markCaptureResolved(capture("failed")));

    expect(result.current.state.captureIds).toEqual([CAPTURE_ID]);
    expect(result.current.state.pendingSubmissions).toEqual([]);

    act(() => result.current.dismissCapture(CAPTURE_ID));

    expect(result.current.state.captureIds).toEqual([]);
    expect(readInboxTrackingState(USER_ID)).toEqual({
      version: 1,
      captureIds: [],
      pendingSubmissions: [],
    });
  });

  it("rehydrates tracked capture IDs after a page reload", async () => {
    window.localStorage.setItem(
      "blaie.inbox.capture-tracking.v1:user-1",
      JSON.stringify({
        version: 1,
        captureIds: [CAPTURE_ID],
        pendingSubmissions: [],
      }),
    );

    const { result } = renderHook(() => useInboxTracking(USER_ID));

    await waitFor(() =>
      expect(result.current.state.captureIds).toEqual([CAPTURE_ID]),
    );
  });

  it("links a response-lost idempotency record to a recovered processing capture", async () => {
    await getOrCreatePendingSubmission(USER_ID, "Call mom tonight");
    const { result } = renderHook(() => useInboxTracking(USER_ID));

    await act(async () => {
      await result.current.rememberRecoveredCapture(capture("processing"));
    });

    expect(result.current.state.captureIds).toEqual([CAPTURE_ID]);
    expect(result.current.state.pendingSubmissions[0]?.captureId).toBe(
      CAPTURE_ID,
    );
  });

  it("ignores corrupted persisted state", () => {
    window.localStorage.setItem(
      "blaie.inbox.capture-tracking.v1:user-1",
      "not-json",
    );

    expect(readInboxTrackingState(USER_ID)).toEqual({
      version: 1,
      captureIds: [],
      pendingSubmissions: [],
    });
  });
});
