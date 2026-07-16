"use client";

import { Inbox, LoaderCircle, RefreshCw, Send } from "lucide-react";
import { FormEvent, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isAppError } from "@/shared/api/errors/app-error";

import { useCreateTextCaptureMutation } from "../model/inbox.mutations";
import { useInboxItemsQuery } from "../model/inbox.queries";
import type { InboxCategory, InboxItem } from "../types/inbox";

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
  reminder:
    "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
  information:
    "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200",
};

export function InboxTestPanel() {
  const [text, setText] = useState("");
  const inboxQuery = useInboxItemsQuery();
  const captureMutation = useCreateTextCaptureMutation();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedText = text.trim();
    if (!trimmedText) {
      toast.error("Enter some text first.");
      return;
    }

    try {
      const capture = await captureMutation.mutateAsync({ text: trimmedText });
      setText("");
      if (capture.items.length === 0) {
        toast.success("Captured. No active Inbox items were found.");
      } else {
        toast.success(
          `${capture.items.length} Inbox ${capture.items.length === 1 ? "item" : "items"} created.`,
        );
      }
    } catch (error) {
      toast.error(
        isAppError(error) ? error.message : "Unable to capture this text.",
      );
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <div className="mb-4 space-y-1">
          <p className="font-label-md text-xs uppercase tracking-[0.14em] text-muted-foreground">
            Temporary test screen
          </p>
          <h1 className="font-display-sm text-3xl tracking-tight">Inbox capture</h1>
          <p className="text-sm text-muted-foreground">
            Write anything. Blaie will send it to the classifier and place the
            extracted records in your Inbox.
          </p>
        </div>

        <form className="space-y-3" onSubmit={submit}>
          <label className="sr-only" htmlFor="inbox-text">
            Text to classify
          </label>
          <textarea
            id="inbox-text"
            value={text}
            maxLength={10_000}
            onChange={(event) => setText(event.target.value)}
            placeholder="Example: I have a meeting at 5 PM, then I need to run and finish my homework"
            className="min-h-32 w-full resize-y rounded-lg border border-input bg-background p-3 text-sm outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          />
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-muted-foreground">{text.length}/10,000</span>
            <Button disabled={captureMutation.isPending} type="submit">
              {captureMutation.isPending ? (
                <LoaderCircle className="animate-spin" />
              ) : (
                <Send />
              )}
              {captureMutation.isPending ? "Classifying..." : "Capture text"}
            </Button>
          </div>
        </form>
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
            <RefreshCw className={inboxQuery.isFetching ? "animate-spin" : ""} />
          </Button>
        </div>

        <InboxList
          error={inboxQuery.error}
          isLoading={inboxQuery.isLoading}
          items={inboxQuery.data ?? []}
        />
      </section>
    </div>
  );
}

function InboxList({
  error,
  isLoading,
  items,
}: {
  error: unknown;
  isLoading: boolean;
  items: InboxItem[];
}) {
  if (isLoading) {
    return (
      <div className="space-y-3 p-5">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  }

  if (error) {
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
    <ul className="divide-y divide-border">
      {items.map((item) => (
        <li className="flex gap-3 px-5 py-4" key={item.id}>
          <div className="min-w-0 flex-1">
            <p className="line-clamp-2 text-sm leading-6">{item.originalText}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {new Intl.DateTimeFormat(undefined, {
                dateStyle: "medium",
                timeStyle: "short",
              }).format(new Date(item.createdAt))}
            </p>
          </div>
          <CategoryBadge category={item.category} status={item.processingStatus} />
        </li>
      ))}
    </ul>
  );
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
