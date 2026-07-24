import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useUser } from "@/features/auth/model/user-context";
import {
  useCreateTextCaptureMutation,
  useDeleteCaptureMutation,
  useRetryCaptureMutation,
} from "@/features/inbox/model/inbox.mutations";
import {
  useInboxItemsQuery,
  usePendingCaptureResolutionQueries,
  useProcessingCapturesQuery,
  useTrackedCaptureQueries,
} from "@/features/inbox/model/inbox.queries";
import { inboxKeys } from "@/features/inbox/model/inbox.keys";
import { useInboxTracking } from "@/features/inbox/model/inbox-tracking";
import type {
  InboxItem,
  TextCapture,
} from "@/features/inbox/types/inbox";
import { InboxPanel } from "@/features/inbox/ui/inbox-panel";
import { createAppError } from "@/shared/api/errors/app-error";
import { toast } from "sonner";

vi.mock("@/features/auth/model/user-context", () => ({
  useUser: vi.fn(),
}));

vi.mock("@/features/inbox/model/inbox.mutations", () => ({
  useCreateTextCaptureMutation: vi.fn(),
  useDeleteCaptureMutation: vi.fn(),
  useRetryCaptureMutation: vi.fn(),
}));

vi.mock("@/features/inbox/model/inbox.queries", async (importOriginal) => {
  const original =
    await importOriginal<
      typeof import("@/features/inbox/model/inbox.queries")
    >();
  return {
    ...original,
    useInboxItemsQuery: vi.fn(),
    usePendingCaptureResolutionQueries: vi.fn(),
    useProcessingCapturesQuery: vi.fn(),
    useTrackedCaptureQueries: vi.fn(),
  };
});

vi.mock("@/features/inbox/model/inbox-tracking", () => ({
  useInboxTracking: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

const refetchInbox = vi.fn().mockResolvedValue(undefined);
const fetchNextPage = vi.fn().mockResolvedValue(undefined);
const beginSubmission = vi.fn();
const discardSubmission = vi.fn();
const dismissCapture = vi.fn();
const markCaptureResolved = vi.fn();
const rememberCapture = vi.fn();
const rememberRecoveredCapture = vi.fn();
const createCapture = vi.fn();
const retryCapture = vi.fn();
const deleteCapture = vi.fn();

function capture(
  id: string,
  processingStatus: TextCapture["processingStatus"],
  overrides: Partial<TextCapture> = {},
): TextCapture {
  return {
    id,
    originalText: `Text for ${id}`,
    processingStatus,
    failureCode:
      processingStatus === "failed" ? "ai_provider_unavailable" : null,
    canRetry: processingStatus === "failed",
    items: [],
    createdAt: "2026-07-17T10:00:00Z",
    updatedAt: "2026-07-17T10:01:00Z",
    ...overrides,
  };
}

function item(id: string, originalText = id): InboxItem {
  return {
    id,
    captureId: "capture-1",
    originalText,
    category: "reminder",
    processingStatus: "completed",
    createdAt: "2026-07-17T10:00:00Z",
  };
}

function mockInbox(items: InboxItem[] = []) {
  vi.mocked(useInboxItemsQuery).mockReturnValue({
    data: {
      pages: [
        { items, nextCursor: null, hasMore: false, limit: 20 },
      ],
      pageParams: [null],
    },
    error: null,
    fetchNextPage,
    hasNextPage: false,
    isFetching: false,
    isFetchingNextPage: false,
    isLoading: false,
    refetch: refetchInbox,
  } as never);
}

function mockTracking(captureIds: string[] = []) {
  vi.mocked(useInboxTracking).mockReturnValue({
    state: { version: 1, captureIds, pendingSubmissions: [] },
    unresolvedSubmissionCount: 0,
    beginSubmission,
    discardSubmission,
    dismissCapture,
    markCaptureResolved,
    rememberCapture,
    rememberRecoveredCapture,
  });
}

function testQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function renderInbox(queryClient = testQueryClient()) {
  function QueryWrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  }

  return { ...render(<InboxPanel />, { wrapper: QueryWrapper }), queryClient };
}

describe("InboxPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useUser).mockReturnValue({
      user: { id: "user-1" },
      isPending: false,
      isError: false,
    } as never);
    mockInbox();
    mockTracking();
    vi.mocked(useProcessingCapturesQuery).mockReturnValue({ data: [] } as never);
    vi.mocked(usePendingCaptureResolutionQueries).mockReturnValue([]);
    vi.mocked(useTrackedCaptureQueries).mockReturnValue([]);
    vi.mocked(useCreateTextCaptureMutation).mockReturnValue({
      isPending: false,
      mutateAsync: createCapture,
    } as never);
    vi.mocked(useRetryCaptureMutation).mockReturnValue({
      isPending: false,
      mutateAsync: retryCapture,
      variables: undefined,
    } as never);
    vi.mocked(useDeleteCaptureMutation).mockReturnValue({
      isPending: false,
      mutateAsync: deleteCapture,
      variables: undefined,
    } as never);
    beginSubmission.mockResolvedValue({
      textHash: "a".repeat(64),
      idempotencyKey: "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
      createdAt: "2026-07-17T10:00:00Z",
      captureId: null,
    });
  });

  it("renders separate cards for multiple concurrent and terminal captures", () => {
    const processing = capture("processing-1", "processing");
    const failed = capture("failed-1", "failed");
    const completed = capture("completed-1", "completed", {
      items: [item("item-1")],
    });
    mockTracking([processing.id, failed.id, completed.id]);
    vi.mocked(useProcessingCapturesQuery).mockReturnValue({
      data: [processing],
    } as never);
    vi.mocked(useTrackedCaptureQueries).mockReturnValue([
      { data: processing, error: null },
      { data: failed, error: null },
      { data: completed, error: null },
    ] as never);

    renderInbox();

    expect(screen.getByText("Text for processing-1")).toBeInTheDocument();
    expect(screen.getByText("Text for failed-1")).toBeInTheDocument();
    expect(screen.getByText("Text for completed-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry capture" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Dismiss capture" })).toHaveLength(2);
    expect(screen.getByText(/provider is temporarily unavailable/i)).toBeInTheDocument();
    expect(screen.queryByText("ai_provider_unavailable")).not.toBeInTheDocument();
  });

  it("removes a terminal capture from the stale processing cache before dismissing it", async () => {
    const staleProcessing = capture("capture-1", "processing");
    const completed = capture("capture-1", "completed", {
      items: [item("item-1")],
    });
    const queryClient = testQueryClient();
    const processingKey = inboxKeys.processing("user-1");
    queryClient.setQueryData(processingKey, [staleProcessing]);
    mockTracking([completed.id]);
    vi.mocked(useProcessingCapturesQuery).mockReturnValue({
      data: [staleProcessing],
    } as never);
    vi.mocked(useTrackedCaptureQueries).mockReturnValue([
      { data: completed, error: null },
    ] as never);

    renderInbox(queryClient);

    await waitFor(() =>
      expect(queryClient.getQueryData(processingKey)).toEqual([]),
    );
    fireEvent.click(screen.getByRole("button", { name: "Dismiss capture" }));

    expect(dismissCapture).toHaveBeenCalledWith("capture-1");
    expect(retryCapture).not.toHaveBeenCalled();
  });

  it("shows the just-in-time privacy disclosure", () => {
    renderInbox();

    expect(screen.getByText(/masks common email, phone, and IP patterns/i)).toBeInTheDocument();
    expect(screen.getByText(/Names and free-form addresses may still be sent/i)).toBeInTheDocument();
  });

  it("restores a failed capture that is no longer in the processing list", () => {
    const restored = capture("failed-after-reload", "failed", {
      failureCode: "sensitive_credential_detected",
      canRetry: false,
    });
    mockTracking([restored.id]);
    vi.mocked(useTrackedCaptureQueries).mockReturnValue([
      { data: restored, error: null },
    ] as never);

    renderInbox();

    expect(screen.getByText("Text for failed-after-reload")).toBeInTheDocument();
    expect(screen.getByText(/appears to contain a secret/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry capture" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dismiss capture" })).toBeInTheDocument();
  });

  it("recovers a terminal capture by idempotency key after an immediate reload", async () => {
    const recovered = capture("completed-after-reload", "completed", {
      originalText: "Call mom",
      items: [item("item-after-reload", "Call mom")],
    });
    vi.mocked(useInboxTracking).mockReturnValue({
      state: {
        version: 1,
        captureIds: [],
        pendingSubmissions: [
          {
            textHash: "a".repeat(64),
            idempotencyKey: "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
            createdAt: "2026-07-17T10:00:00Z",
            captureId: null,
          },
        ],
      },
      unresolvedSubmissionCount: 1,
      beginSubmission,
      discardSubmission,
      dismissCapture,
      markCaptureResolved,
      rememberCapture,
      rememberRecoveredCapture,
    });
    vi.mocked(usePendingCaptureResolutionQueries).mockReturnValue([
      { data: recovered, error: null },
    ] as never);

    renderInbox();

    await waitFor(() =>
      expect(rememberRecoveredCapture).toHaveBeenCalledWith(recovered),
    );
    expect(createCapture).not.toHaveBeenCalled();
  });

  it("renders unique Inbox items and loads the next cursor page", () => {
    vi.mocked(useInboxItemsQuery).mockReturnValue({
      data: {
        pages: [
          {
            items: [item("item-1", "First item")],
            nextCursor: "next",
            hasMore: true,
            limit: 20,
          },
          {
            items: [
              item("item-1", "Duplicate item"),
              item("item-2", "Second item"),
            ],
            nextCursor: null,
            hasMore: false,
            limit: 20,
          },
        ],
        pageParams: [null, "next"],
      },
      error: null,
      fetchNextPage,
      hasNextPage: true,
      isFetching: false,
      isFetchingNextPage: false,
      isLoading: false,
      refetch: refetchInbox,
    } as never);

    renderInbox();

    expect(screen.getByText("First item")).toBeInTheDocument();
    expect(screen.queryByText("Duplicate item")).not.toBeInTheDocument();
    expect(screen.getByText("Second item")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(fetchNextPage).toHaveBeenCalledOnce();
  });

  it.each([
    [
      "RATE_LIMITED",
      429,
      45,
      "Too many capture requests. Try again in 45 seconds.",
    ],
    [
      "CAPTURE_PROCESSING_OVERLOADED",
      503,
      120,
      "Capture processing is busy. Try again in about 2 minutes.",
    ],
  ])(
    "shows safe retry timing for %s",
    async (code, status, retryAfterSeconds, expected) => {
      createCapture.mockRejectedValue(
        createAppError({
          code,
          status,
          message: "backend message",
          retryAfterSeconds,
        }),
      );
      renderInbox();

      fireEvent.change(screen.getByLabelText("Text to classify"), {
        target: { value: "Call mom" },
      });
      fireEvent.submit(
        screen.getByRole("button", { name: "Capture text" }).closest("form")!,
      );

      await waitFor(() => expect(toast.error).toHaveBeenCalledWith(expected));
    },
  );

  it("does not create duplicate submissions while the first submit is pending", async () => {
    let resolveCapture!: (value: TextCapture) => void;
    createCapture.mockReturnValue(
      new Promise<TextCapture>((resolve) => {
        resolveCapture = resolve;
      }),
    );
    renderInbox();
    fireEvent.change(screen.getByLabelText("Text to classify"), {
      target: { value: "Call mom" },
    });
    const form = screen.getByRole("button", { name: "Capture text" }).closest("form")!;

    fireEvent.submit(form);
    fireEvent.submit(form);

    await waitFor(() => expect(beginSubmission).toHaveBeenCalledOnce());
    expect(createCapture).toHaveBeenCalledOnce();

    resolveCapture(capture("capture-1", "processing"));
    await waitFor(() => expect(rememberCapture).toHaveBeenCalledOnce());
  });

  it("pauses idempotency recovery while a normal capture POST is pending", async () => {
    let finishCapture!: (value: TextCapture) => void;
    createCapture.mockReturnValue(
      new Promise<TextCapture>((resolve) => {
        finishCapture = resolve;
      }),
    );
    renderInbox();
    fireEvent.change(screen.getByLabelText("Text to classify"), {
      target: { value: "Call mom" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Capture text" }).closest("form")!,
    );

    await waitFor(() =>
      expect(usePendingCaptureResolutionQueries).toHaveBeenLastCalledWith(
        "user-1",
        [],
        false,
      ),
    );

    finishCapture(capture("capture-1", "processing"));
    await waitFor(() => expect(rememberCapture).toHaveBeenCalledOnce());
  });

  it("retries the failed capture represented by the selected card", async () => {
    const failed = capture("failed-1", "failed");
    mockTracking([failed.id]);
    vi.mocked(useTrackedCaptureQueries).mockReturnValue([
      { data: failed, error: null },
    ] as never);
    retryCapture.mockResolvedValue(capture("failed-1", "processing"));
    renderInbox();

    fireEvent.click(screen.getByRole("button", { name: "Retry capture" }));

    await waitFor(() => expect(retryCapture).toHaveBeenCalledWith("failed-1"));
  });

  it("deletes the source capture selected from an Inbox item", async () => {
    mockInbox([item("item-1", "Delete me")]);
    deleteCapture.mockResolvedValue(undefined);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    renderInbox();

    fireEvent.click(
      screen.getByRole("button", {
        name: "Delete source capture for Delete me",
      }),
    );

    await waitFor(() => expect(deleteCapture).toHaveBeenCalledWith("capture-1"));
    expect(dismissCapture).toHaveBeenCalledWith("capture-1");
  });

  it("stops tracking a capture before its delete request settles", async () => {
    let finishDelete!: () => void;
    deleteCapture.mockReturnValue(
      new Promise<void>((resolve) => {
        finishDelete = resolve;
      }),
    );
    mockTracking(["capture-1"]);
    mockInbox([item("item-1", "Delete without a trailing GET")]);
    vi.spyOn(window, "confirm").mockReturnValue(true);

    renderInbox();
    fireEvent.click(
      screen.getByRole("button", {
        name: "Delete source capture for Delete without a trailing GET",
      }),
    );

    await waitFor(() =>
      expect(useTrackedCaptureQueries).toHaveBeenLastCalledWith(
        "user-1",
        [],
        [],
      ),
    );
    expect(dismissCapture).not.toHaveBeenCalled();

    finishDelete();
    await waitFor(() =>
      expect(dismissCapture).toHaveBeenCalledWith("capture-1"),
    );
  });

  it("discards the persisted submission key after a deterministic privacy rejection", async () => {
    createCapture.mockRejectedValue(
      createAppError({
        code: "CAPTURE_SENSITIVE_CONTENT",
        status: 422,
        message: "Capture contains sensitive content",
      }),
    );
    renderInbox();
    fireEvent.change(screen.getByLabelText("Text to classify"), {
      target: { value: "secret" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Capture text" }).closest("form")!,
    );

    await waitFor(() =>
      expect(discardSubmission).toHaveBeenCalledWith(
        "5db7af5d-d6dc-4da1-bcd9-f4f02bc693ef",
      ),
    );
  });
});
