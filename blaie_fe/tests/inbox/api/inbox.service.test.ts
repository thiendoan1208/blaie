import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  createTextCapture,
  deleteCapture,
  getInboxItems,
  resolveCapture,
} from "@/features/inbox/api/inbox.service";
import type {
  InboxItem,
  TextCapture,
} from "@/features/inbox/types/inbox";
import { httpClient } from "@/shared/api/http-client";

vi.mock("@/shared/api/http-client", () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const capture: TextCapture = {
  id: "capture-1",
  originalText: "Call mom",
  processingStatus: "processing",
  failureCode: null,
  canRetry: false,
  items: [],
  createdAt: "2026-07-17T10:00:00Z",
  updatedAt: "2026-07-17T10:00:00Z",
};

const item: InboxItem = {
  id: "item-1",
  captureId: "capture-1",
  originalText: "Call mom",
  category: "reminder",
  processingStatus: "completed",
  createdAt: "2026-07-17T10:01:00Z",
};

describe("Inbox service", () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset();
    vi.mocked(httpClient.post).mockReset();
    vi.mocked(httpClient.delete).mockReset();
  });

  it("deletes the owned source capture", async () => {
    vi.mocked(httpClient.delete).mockResolvedValue({ data: null });

    await deleteCapture("capture-1");

    expect(httpClient.delete).toHaveBeenCalledWith("/captures/capture-1");
  });

  it("sends the persisted idempotency key on capture submission", async () => {
    vi.mocked(httpClient.post).mockResolvedValue({
      data: { data: capture },
    });

    await createTextCapture({
      text: "Call mom",
      idempotencyKey: "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
    });

    expect(httpClient.post).toHaveBeenCalledWith(
      "/captures/text",
      { text: "Call mom" },
      {
        headers: {
          "Idempotency-Key": "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
        },
      },
    );
  });

  it("resolves an uncertain capture without resubmitting its text", async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: { data: { ...capture, processingStatus: "completed" } },
    });

    await resolveCapture("5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef");

    expect(httpClient.get).toHaveBeenCalledWith("/captures/resolve", {
      headers: {
        "Idempotency-Key": "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
      },
    });
    expect(httpClient.post).not.toHaveBeenCalled();
  });

  it("maps cursor metadata and sends the next cursor", async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: {
        data: [item],
        meta: { nextCursor: "cursor-2", hasMore: true, limit: 20 },
      },
    });

    await expect(
      getInboxItems({ cursor: "cursor-1", limit: 20 }),
    ).resolves.toEqual({
      items: [item],
      nextCursor: "cursor-2",
      hasMore: true,
      limit: 20,
    });
    expect(httpClient.get).toHaveBeenCalledWith("/inbox", {
      params: { cursor: "cursor-1", limit: 20 },
    });
  });
});
