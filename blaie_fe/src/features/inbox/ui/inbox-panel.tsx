"use client";

import {
  AlertTriangle,
  CheckCircle2,
  Inbox,
  LoaderCircle,
  RefreshCw,
  Send,
  Trash2,
  X,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import {
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useUser } from "@/features/auth/model/user-context";
import { isAppError } from "@/shared/api/errors/app-error";

import {
  captureFailureMessage,
  captureRequestErrorMessage,
  shouldDiscardCaptureSubmission,
} from "../model/inbox-errors";
import {
  useCreateTextCaptureMutation,
  useDeleteCaptureMutation,
  useRetryCaptureMutation,
} from "../model/inbox.mutations";
import {
  removeCaptureFromProcessingCache,
  uniqueInboxItems,
  useInboxItemsQuery,
  useProcessingCapturesQuery,
  useTrackedCaptureQueries,
} from "../model/inbox.queries";
import { useInboxTracking } from "../model/inbox-tracking";
import type {
  InboxCategory,
  InboxItem,
  TextCapture,
} from "../types/inbox";

const categoryLabels: Record<InboxCategory, string> = {
  task: "Task",
  calendar_event: "Calendar event",
  reminder: "Reminder",
  information: "Information",
};

const categoryStyles: Record<InboxCategory, string> = {
  task: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-200",
  calendar_event:
    "bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-200",
  reminder: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
  information:
    "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200",
};

export function InboxPanel() {
  const { user } = useUser();
  if (!user) return null;
  return <InboxPanelContent key={user.id} userId={user.id} />;
}

function InboxPanelContent({ userId }: { userId: string }) {
  const [text, setText] = useState("");
  const [preparingSubmission, setPreparingSubmission] = useState(false);
  const submitInFlight = useRef(false);
  const notifiedTerminalStates = useRef(new Set<string>());
  const queryClient = useQueryClient();

  const tracking = useInboxTracking(userId);
  const {
    beginSubmission,
    discardSubmission,
    dismissCapture,
    markCaptureResolved,
    rememberCapture,
    rememberRecoveredCapture,
    state: trackingState,
    unresolvedSubmissionCount,
  } = tracking;
  const inboxQuery = useInboxItemsQuery(userId);
  const processingQuery = useProcessingCapturesQuery(userId);
  const captureQueries = useTrackedCaptureQueries(
    userId,
    trackingState.captureIds,
    processingQuery.data ?? [],
  );
  const captureMutation = useCreateTextCaptureMutation(userId);
  const retryMutation = useRetryCaptureMutation(userId);
  const deleteMutation = useDeleteCaptureMutation(userId);

  const forgetCapture = useCallback(
    (captureId: string) => {
      removeCaptureFromProcessingCache(queryClient, userId, captureId);
      dismissCapture(captureId);
    },
    [dismissCapture, queryClient, userId],
  );

  const captures = useMemo(() => {
    const byId = new Map<string, TextCapture>();
    for (const capture of processingQuery.data ?? []) {
      byId.set(capture.id, capture);
    }
    for (const query of captureQueries) {
      if (query.data) byId.set(query.data.id, query.data);
    }
    return [...byId.values()].sort(
      (left, right) =>
        Date.parse(right.createdAt) - Date.parse(left.createdAt),
    );
  }, [captureQueries, processingQuery.data]);

  const inboxItems = uniqueInboxItems(inboxQuery.data);

  useEffect(() => {
    for (const capture of processingQuery.data ?? []) {
      void rememberRecoveredCapture(capture);
    }
  }, [processingQuery.data, rememberRecoveredCapture]);

  useEffect(() => {
    captureQueries.forEach((query, index) => {
      if (isAppError(query.error) && query.error.status === 404) {
        const captureId = trackingState.captureIds[index];
        if (captureId) forgetCapture(captureId);
      }
    });
  }, [captureQueries, forgetCapture, trackingState.captureIds]);

  useEffect(() => {
    for (const capture of captures) {
      if (capture.processingStatus === "processing") continue;
      const notificationKey = `${capture.id}:${capture.processingStatus}:${capture.updatedAt}`;
      if (notifiedTerminalStates.current.has(notificationKey)) continue;
      notifiedTerminalStates.current.add(notificationKey);
      removeCaptureFromProcessingCache(queryClient, userId, capture.id);
      markCaptureResolved(capture);

      if (capture.processingStatus === "completed") {
        void inboxQuery.refetch();
        toast.success(
          capture.items.length === 0
            ? "Captured. No active Inbox items were found."
            : `${capture.items.length} Inbox ${capture.items.length === 1 ? "item" : "items"} created.`,
        );
      } else {
        toast.error(captureFailureMessage(capture.failureCode));
      }
    }
  }, [captures, inboxQuery, markCaptureResolved, queryClient, userId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitInFlight.current) return;

    const trimmedText = text.trim();
    if (!trimmedText) {
      toast.error("Enter some text first.");
      return;
    }

    submitInFlight.current = true;
    setPreparingSubmission(true);
    let submission: Awaited<ReturnType<typeof beginSubmission>> | undefined;
    try {
      submission = await beginSubmission(trimmedText);
      const capture = await captureMutation.mutateAsync({
        text: trimmedText,
        idempotencyKey: submission.idempotencyKey,
      });
      rememberCapture(submission, capture);
      setText("");
      toast.success("Capture accepted and queued for classification.");
    } catch (error) {
      if (submission && shouldDiscardCaptureSubmission(error)) {
        discardSubmission(submission.idempotencyKey);
      }
      toast.error(captureRequestErrorMessage(error));
    } finally {
      submitInFlight.current = false;
      setPreparingSubmission(false);
    }
  }

  async function remove(captureId: string) {
    if (!window.confirm("Delete this source capture and all Inbox items created from it?")) return;
    try {
      await deleteMutation.mutateAsync(captureId);
      forgetCapture(captureId);
      toast.success("Capture and its Inbox items were deleted.");
    } catch (error) {
      toast.error(captureRequestErrorMessage(error));
    }
  }

  async function retry(captureId: string) {
    try {
      await retryMutation.mutateAsync(captureId);
      toast.success("Capture queued for another classification attempt.");
    } catch (error) {
      toast.error(captureRequestErrorMessage(error));
    }
  }

  const submitting = preparingSubmission || captureMutation.isPending;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <div className="mb-4 space-y-1">
          <p className="font-label-md text-xs uppercase tracking-[0.14em] text-muted-foreground">
            Asynchronous Inbox
          </p>
          <h1 className="font-display-sm text-3xl tracking-tight">
            Inbox capture
          </h1>
          <p className="text-sm text-muted-foreground">
            Write anything. Blaie will classify it in the background and place
            the extracted records in your Inbox.
          </p>
          <p className="text-xs text-muted-foreground">
            Blaie stores your text and masks common email, phone, and IP
            patterns before AI processing. Names and free-form addresses may
            still be sent to the configured provider. Never paste passwords,
            API keys, payment cards, or government IDs.
          </p>
        </div>

        <form className="space-y-3" onSubmit={submit}>
          <label className="sr-only" htmlFor="inbox-text">
            Text to classify
          </label>
          <textarea
            spellCheck={false}
            id="inbox-text"
            value={text}
            maxLength={10_000}
            disabled={submitting}
            onChange={(event) => setText(event.target.value)}
            placeholder="Example: I have a meeting at 5 PM, then I need to run and finish my homework"
            className="min-h-32 w-full resize-y rounded-lg border border-input bg-background p-3 text-sm outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60"
          />
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-muted-foreground">
              {text.length}/10,000
            </span>
            <Button disabled={submitting} type="submit">
              {submitting ? (
                <LoaderCircle className="animate-spin" />
              ) : (
                <Send />
              )}
              {submitting ? "Submitting..." : "Capture text"}
            </Button>
          </div>
        </form>

        {unresolvedSubmissionCount > 0 && (
          <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-900 dark:bg-amber-950 dark:text-amber-100">
            A previous request has an uncertain result. Submitting the same text
            will safely reuse its request key.
          </p>
        )}

        <CaptureCards
          captures={captures}
          retryingCaptureId={
            retryMutation.isPending ? retryMutation.variables : undefined
          }
          onDismiss={forgetCapture}
          deletingCaptureId={
            deleteMutation.isPending ? deleteMutation.variables : undefined
          }
          onDelete={(captureId) => void remove(captureId)}
          onRetry={(captureId) => void retry(captureId)}
        />
      </section>

      <section className="rounded-xl border border-border bg-card shadow-sm">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="flex items-center gap-2">
            <Inbox className="size-4 text-muted-foreground" />
            <h2 className="font-medium">Recent Inbox items</h2>
          </div>
          <Button
            aria-label="Refresh Inbox"
            disabled={inboxQuery.isFetching}
            size="icon-sm"
            variant="ghost"
            onClick={() => void inboxQuery.refetch()}
          >
            <RefreshCw
              className={inboxQuery.isFetching ? "animate-spin" : ""}
            />
          </Button>
        </div>

        <InboxList
          error={inboxQuery.error}
          hasMore={inboxQuery.hasNextPage}
          isLoading={inboxQuery.isLoading}
          isLoadingMore={inboxQuery.isFetchingNextPage}
          items={inboxItems}
          deletingCaptureId={
            deleteMutation.isPending ? deleteMutation.variables : undefined
          }
          onDelete={(captureId) => void remove(captureId)}
          onLoadMore={() => void inboxQuery.fetchNextPage()}
        />
      </section>
    </div>
  );
}

function CaptureCards({
  captures,
  deletingCaptureId,
  retryingCaptureId,
  onDelete,
  onDismiss,
  onRetry,
}: {
  captures: TextCapture[];
  deletingCaptureId: string | undefined;
  retryingCaptureId: string | undefined;
  onDismiss: (captureId: string) => void;
  onDelete: (captureId: string) => void;
  onRetry: (captureId: string) => void;
}) {
  if (captures.length === 0) return null;

  return (
    <div className="mt-5 space-y-3" aria-label="Capture status">
      {captures.map((capture) => {
        const processing = capture.processingStatus === "processing";
        const failed = capture.processingStatus === "failed";
        const retrying = retryingCaptureId === capture.id;
        const deleting = deletingCaptureId === capture.id;
        return (
          <article
            className="rounded-lg border border-border bg-background p-4"
            data-capture-status={capture.processingStatus}
            key={capture.id}
          >
            <div className="flex items-start gap-3">
              {processing ? (
                <LoaderCircle className="mt-0.5 size-4 shrink-0 animate-spin text-muted-foreground" />
              ) : failed ? (
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
              ) : (
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
              )}
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-medium capitalize">
                    {capture.processingStatus}
                  </span>
                  <time
                    className="text-xs text-muted-foreground"
                    dateTime={capture.createdAt}
                  >
                    {formatDate(capture.createdAt)}
                  </time>
                </div>
                <p className="mt-1 line-clamp-2 text-sm leading-6">
                  {capture.originalText}
                </p>
                <p className="mt-2 text-xs text-muted-foreground">
                  {processing
                    ? "Classification is running. You can safely leave this screen."
                    : failed
                      ? captureFailureMessage(capture.failureCode)
                      : `${capture.items.length} Inbox ${capture.items.length === 1 ? "item" : "items"} created.`}
                </p>
              </div>
            </div>

            <div className="mt-3 flex justify-end gap-2">
                <Button
                  aria-label="Delete source capture"
                  disabled={deleting}
                  size="sm"
                  variant="destructive"
                  onClick={() => onDelete(capture.id)}
                >
                  {deleting ? <LoaderCircle className="animate-spin" /> : <Trash2 />}
                  {deleting ? "Deleting..." : "Delete"}
                </Button>
              {!processing && (
                <>
                {failed && capture.canRetry && (
                  <Button
                    aria-label="Retry capture"
                    disabled={retrying}
                    size="sm"
                    variant="outline"
                    onClick={() => onRetry(capture.id)}
                  >
                    <RefreshCw className={retrying ? "animate-spin" : ""} />
                    {retrying ? "Retrying..." : "Retry"}
                  </Button>
                )}
                <Button
                  aria-label="Dismiss capture"
                  size="sm"
                  variant="ghost"
                  onClick={() => onDismiss(capture.id)}
                >
                  <X />
                  Dismiss
                </Button>
                </>
              )}
            </div>
          </article>
        );
      })}
    </div>
  );
}

function InboxList({
  error,
  isLoading,
  items,
  hasMore,
  isLoadingMore,
  onLoadMore,
  deletingCaptureId,
  onDelete,
}: {
  error: unknown;
  isLoading: boolean;
  items: InboxItem[];
  hasMore: boolean;
  isLoadingMore: boolean;
  onLoadMore: () => void;
  deletingCaptureId: string | undefined;
  onDelete: (captureId: string) => void;
}) {
  if (isLoading) {
    return (
      <div className="space-y-3 p-5">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  }

  if (error && items.length === 0) {
    return (
      <p className="p-5 text-sm text-destructive">
        {isAppError(error) ? error.message : "Unable to load Inbox."}
      </p>
    );
  }

  if (items.length === 0) {
    return (
      <p className="p-8 text-center text-sm text-muted-foreground">
        Nothing captured yet.
      </p>
    );
  }

  return (
    <>
      <ul className="divide-y divide-border">
        {items.map((item) => (
          <li className="flex gap-3 px-5 py-4" key={item.id}>
            <div className="min-w-0 flex-1">
              <p className="line-clamp-2 text-sm leading-6">
                {item.originalText}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {formatDate(item.createdAt)}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <CategoryBadge
                category={item.category}
                status={item.processingStatus}
              />
              <Button
                aria-label={`Delete source capture for ${item.originalText}`}
                disabled={deletingCaptureId === item.captureId}
                size="icon-sm"
                variant="ghost"
                onClick={() => onDelete(item.captureId)}
              >
                {deletingCaptureId === item.captureId ? (
                  <LoaderCircle className="animate-spin" />
                ) : (
                  <Trash2 />
                )}
              </Button>
            </div>
          </li>
        ))}
      </ul>
      {hasMore && (
        <div className="border-t border-border p-4 text-center">
          <Button
            disabled={isLoadingMore}
            size="sm"
            variant="outline"
            onClick={onLoadMore}
          >
            {isLoadingMore && <LoaderCircle className="animate-spin" />}
            {isLoadingMore ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </>
  );
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function CategoryBadge({
  category,
  status,
}: {
  category: InboxCategory | null;
  status: InboxItem["processingStatus"];
}) {
  if (!category) {
    return (
      <span className="h-fit shrink-0 rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground">
        {status}
      </span>
    );
  }

  return (
    <span
      className={`h-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${categoryStyles[category]}`}
    >
      {categoryLabels[category]}
    </span>
  );
}
