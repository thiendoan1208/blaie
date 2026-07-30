import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  createTextCapture,
  deleteCapture,
  getCapture,
  getInboxItems,
  getProcessingCaptures,
  resolveCapture,
} from "@/features/inbox/api/inbox.service";
import {
  useCreateTextCaptureMutation,
  useDeleteCaptureMutation,
} from "@/features/inbox/model/inbox.mutations";
import {
  captureResolutionRetryDelay,
  capturePollingInterval,
  shouldRetryCaptureResolution,
  uniqueInboxItems,
  useInboxItemsQuery,
  usePendingCaptureResolutionQueries,
  useProcessingCapturesQuery,
} from "@/features/inbox/model/inbox.queries";
import type {
  InboxItem,
  InboxPage,
  TextCapture,
} from "@/features/inbox/types/inbox";
import { createAppError } from "@/shared/api/errors/app-error";

vi.mock("@/features/inbox/api/inbox.service", () => ({
  createTextCapture: vi.fn(),
  deleteCapture: vi.fn(),
  getCapture: vi.fn(),
  getInboxItems: vi.fn(),
  getProcessingCaptures: vi.fn(),
  resolveCapture: vi.fn(),
  retryCapture: vi.fn(),
}));

function capture(status: TextCapture["processingStatus"]): TextCapture {
  return {
    id: "capture-1",
    inputType: "text",
    originalText: "Call mom",
    processingStatus: status,
    failureCode: null,
    canRetry: false,
    attachments: [],
    items: [],
    createdAt: "2026-07-17T10:00:00Z",
    updatedAt: "2026-07-17T10:00:00Z",
  };
}

function item(id: string, text = id): InboxItem {
  return {
    id,
    captureId: "capture-1",
    originalText: text,
    category: "task",
    processingStatus: "completed",
    createdAt: "2026-07-17T10:00:00Z",
  };
}

function page(
  items: InboxItem[],
  nextCursor: string | null,
): InboxPage {
  return {
    items,
    nextCursor,
    hasMore: nextCursor !== null,
    limit: 20,
  };
}

function testQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function wrapper(queryClient: QueryClient) {
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  };
}

describe("Inbox queries and mutations", () => {
  beforeEach(() => {
    vi.mocked(createTextCapture).mockReset();
    vi.mocked(deleteCapture).mockReset();
    vi.mocked(getCapture).mockReset();
    vi.mocked(getInboxItems).mockReset();
    vi.mocked(getProcessingCaptures).mockReset();
    vi.mocked(resolveCapture).mockReset();
  });

  it("loads cursor pages and removes duplicate items across page boundaries", async () => {
    vi.mocked(getInboxItems)
      .mockResolvedValueOnce(page([item("item-1")], "cursor-2"))
      .mockResolvedValueOnce(
        page([item("item-1", "duplicate"), item("item-2")], null),
      );
    const queryClient = testQueryClient();
    const { result } = renderHook(() => useInboxItemsQuery("user-1"), {
      wrapper: wrapper(queryClient),
    });

    await waitFor(() => expect(result.current.hasNextPage).toBe(true));
    await act(async () => {
      await result.current.fetchNextPage();
    });
    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));

    expect(getInboxItems).toHaveBeenNthCalledWith(1, {
      cursor: null,
      limit: 20,
    });
    expect(getInboxItems).toHaveBeenNthCalledWith(2, {
      cursor: "cursor-2",
      limit: 20,
    });
    expect(uniqueInboxItems(result.current.data).map(({ id }) => id)).toEqual([
      "item-1",
      "item-2",
    ]);
    expect(result.current.hasNextPage).toBe(false);
  });

  it("polls only while a capture is processing", () => {
    expect(capturePollingInterval(capture("processing"))).toBe(1_500);
    expect(capturePollingInterval(capture("completed"))).toBe(false);
    expect(capturePollingInterval(capture("failed"))).toBe(false);
    expect(capturePollingInterval(undefined)).toBe(false);
  });

  it.each([
    ["network errors", 0],
    ["request timeouts", 408],
    ["early requests", 425],
    ["rate limits", 429],
    ["server errors", 503],
  ])("retries transient capture resolution %s", (_, status) => {
    const error = createAppError({
      code: status === 0 ? "NETWORK_ERROR" : "HTTP_ERROR",
      status,
      message: "Temporary failure",
    });

    expect(shouldRetryCaptureResolution(0, error)).toBe(true);
    expect(shouldRetryCaptureResolution(4, error)).toBe(true);
    expect(shouldRetryCaptureResolution(5, error)).toBe(false);
  });

  it("retries a missing capture briefly to cover commit races", () => {
    const error = createAppError({
      code: "CAPTURE_NOT_FOUND",
      status: 404,
      message: "Capture not found",
    });

    expect(shouldRetryCaptureResolution(0, error)).toBe(true);
    expect(shouldRetryCaptureResolution(2, error)).toBe(true);
    expect(shouldRetryCaptureResolution(3, error)).toBe(false);
  });

  it.each([400, 401, 403, 409, 422])(
    "does not retry deterministic capture resolution status %s",
    (status) => {
      const error = createAppError({
        code: "HTTP_ERROR",
        status,
        message: "Deterministic failure",
      });

      expect(shouldRetryCaptureResolution(0, error)).toBe(false);
    },
  );

  it("bounds retries for unnormalized resolution failures", () => {
    const error = new Error("Unknown failure");

    expect(shouldRetryCaptureResolution(3, error)).toBe(true);
    expect(shouldRetryCaptureResolution(4, error)).toBe(false);
  });

  it("backs off resolution retries and honors Retry-After", () => {
    expect(captureResolutionRetryDelay(0, new Error("failure"))).toBe(1_000);
    expect(captureResolutionRetryDelay(1, new Error("failure"))).toBe(2_000);
    expect(captureResolutionRetryDelay(4, new Error("failure"))).toBe(10_000);
    expect(captureResolutionRetryDelay(10, new Error("failure"))).toBe(10_000);

    const rateLimited = createAppError({
      code: "RATE_LIMITED",
      status: 429,
      message: "Wait",
      retryAfterSeconds: 30,
    });
    const immediateRetry = createAppError({
      code: "RATE_LIMITED",
      status: 429,
      message: "Retry now",
      retryAfterSeconds: 0,
    });

    expect(captureResolutionRetryDelay(0, rateLimited)).toBe(30_000);
    expect(captureResolutionRetryDelay(0, immediateRetry)).toBe(1_000);
  });

  it("enables focus and reconnect recovery for the processing list", async () => {
    vi.mocked(getProcessingCaptures).mockResolvedValue([]);
    const queryClient = testQueryClient();
    renderHook(() => useProcessingCapturesQuery("user-1"), {
      wrapper: wrapper(queryClient),
    });

    await waitFor(() => expect(getProcessingCaptures).toHaveBeenCalledOnce());
    const query = queryClient.getQueryCache().find({
      queryKey: ["inbox", "user", "user-1", "processing"],
    });
    expect(query?.options.refetchOnWindowFocus).toBe(true);
    expect(query?.options.refetchOnReconnect).toBe(true);
  });

  it("does not resolve an idempotency key while its POST is still active", async () => {
    vi.mocked(resolveCapture).mockResolvedValue(capture("processing"));
    const queryClient = testQueryClient();
    const pendingSubmissions = [
      {
        inputType: "text",
        requestHash: "a".repeat(64),
        idempotencyKey: "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
        createdAt: "2026-07-17T10:00:00Z",
        captureId: null,
      },
    ];
    const { rerender } = renderHook(
      ({ recoveryEnabled }) =>
        usePendingCaptureResolutionQueries(
          "user-1",
          pendingSubmissions,
          recoveryEnabled,
        ),
      {
        initialProps: { recoveryEnabled: false },
        wrapper: wrapper(queryClient),
      },
    );

    await act(async () => Promise.resolve());
    expect(resolveCapture).not.toHaveBeenCalled();

    rerender({ recoveryEnabled: true });
    await waitFor(() =>
      expect(resolveCapture).toHaveBeenCalledWith(
        "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
      ),
    );
    const query = queryClient.getQueryCache().find({
      queryKey: [
        "inbox",
        "user",
        "user-1",
        "resolution",
        "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
      ],
    });
    expect(query?.options.refetchOnWindowFocus).toBe("always");
    expect(query?.options.refetchOnReconnect).toBe("always");
    expect(query?.options.staleTime).toBe(0);
  });

  it("uses the same idempotency key when React Query retries an ambiguous POST", async () => {
    const input = {
      text: "Call mom",
      idempotencyKey: "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
    };
    vi.mocked(createTextCapture)
      .mockRejectedValueOnce(
        createAppError({
          code: "NETWORK_ERROR",
          status: 0,
          message: "Network unavailable",
        }),
      )
      .mockResolvedValueOnce(capture("processing"));
    const queryClient = testQueryClient();
    const { result } = renderHook(
      () => useCreateTextCaptureMutation("user-1"),
      { wrapper: wrapper(queryClient) },
    );

    await act(async () => {
      await result.current.mutateAsync(input);
    });

    expect(createTextCapture).toHaveBeenCalledTimes(2);
    expect(vi.mocked(createTextCapture).mock.calls[0]?.[0]).toEqual(input);
    expect(vi.mocked(createTextCapture).mock.calls[1]?.[0]).toEqual(input);
  });

  it("removes the deleted capture cache and invalidates user-scoped lists", async () => {
    vi.mocked(deleteCapture).mockResolvedValue();
    const queryClient = testQueryClient();
    queryClient.setQueryData(
      ["inbox", "user", "user-1", "capture", "capture-1"],
      capture("completed"),
    );
    queryClient.setQueryData(["inbox", "user", "user-1", "list"], {});
    queryClient.setQueryData(
      ["inbox", "user", "user-1", "processing"],
      [],
    );
    const { result } = renderHook(() => useDeleteCaptureMutation("user-1"), {
      wrapper: wrapper(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync("capture-1");
    });

    expect(vi.mocked(deleteCapture).mock.calls[0]?.[0]).toBe("capture-1");
    expect(
      queryClient.getQueryData([
        "inbox",
        "user",
        "user-1",
        "capture",
        "capture-1",
      ]),
    ).toBeUndefined();
    expect(
      queryClient.getQueryState(["inbox", "user", "user-1", "list"])
        ?.isInvalidated,
    ).toBe(true);
  });

  it("treats a missing capture as an idempotent delete success", async () => {
    vi.mocked(deleteCapture).mockRejectedValue(
      createAppError({
        code: "CAPTURE_NOT_FOUND",
        status: 404,
        message: "Capture not found",
      }),
    );
    const queryClient = testQueryClient();
    queryClient.setQueryData(
      ["inbox", "user", "user-1", "capture", "capture-1"],
      capture("completed"),
    );
    const { result } = renderHook(() => useDeleteCaptureMutation("user-1"), {
      wrapper: wrapper(queryClient),
    });

    await act(async () => {
      await expect(result.current.mutateAsync("capture-1")).resolves.toBeUndefined();
    });

    expect(
      queryClient.getQueryData([
        "inbox",
        "user",
        "user-1",
        "capture",
        "capture-1",
      ]),
    ).toBeUndefined();
  });

  it("revalidates private caches after an ambiguous delete failure", async () => {
    vi.mocked(deleteCapture).mockRejectedValue(
      createAppError({
        code: "NETWORK_ERROR",
        status: 0,
        message: "Network unavailable",
      }),
    );
    const queryClient = testQueryClient();
    const captureKey = ["inbox", "user", "user-1", "capture", "capture-1"];
    const listKey = ["inbox", "user", "user-1", "list"];
    queryClient.setQueryData(captureKey, capture("completed"));
    queryClient.setQueryData(listKey, {});
    const { result } = renderHook(() => useDeleteCaptureMutation("user-1"), {
      wrapper: wrapper(queryClient),
    });

    await act(async () => {
      await expect(result.current.mutateAsync("capture-1")).rejects.toMatchObject({ status: 0 });
    });

    expect(queryClient.getQueryState(captureKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(listKey)?.isInvalidated).toBe(true);
  });
});
