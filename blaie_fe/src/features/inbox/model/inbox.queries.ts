import {
  type InfiniteData,
  useInfiniteQuery,
  useQueries,
  useQuery,
} from "@tanstack/react-query";

import {
  getCapture,
  getInboxItems,
  getProcessingCaptures,
} from "../api/inbox.service";
import type { InboxItem, InboxPage, TextCapture } from "../types/inbox";
import { inboxKeys } from "./inbox.keys";

const INBOX_PAGE_SIZE = 20;
const CAPTURE_POLL_INTERVAL_MS = 1_500;

export function capturePollingInterval(
  capture: TextCapture | undefined,
): number | false {
  return capture?.processingStatus === "processing"
    ? CAPTURE_POLL_INTERVAL_MS
    : false;
}

export function useInboxItemsQuery(userId: string) {
  return useInfiniteQuery({
    queryKey: inboxKeys.list(userId),
    queryFn: ({ pageParam }) =>
      getInboxItems({ cursor: pageParam, limit: INBOX_PAGE_SIZE }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) =>
      lastPage.hasMore && lastPage.nextCursor
        ? lastPage.nextCursor
        : undefined,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  });
}

export function uniqueInboxItems(
  data: InfiniteData<InboxPage, unknown> | undefined,
): InboxItem[] {
  if (!data) return [];

  const unique = new Map<string, InboxItem>();
  for (const page of data.pages) {
    for (const item of page.items) {
      if (!unique.has(item.id)) unique.set(item.id, item);
    }
  }
  return [...unique.values()];
}

export function useProcessingCapturesQuery(userId: string) {
  return useQuery({
    queryKey: inboxKeys.processing(userId),
    queryFn: getProcessingCaptures,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  });
}

export function useTrackedCaptureQueries(
  userId: string,
  captureIds: string[],
  processingCaptures: TextCapture[],
) {
  const processingById = new Map(
    processingCaptures.map((capture) => [capture.id, capture]),
  );

  return useQueries({
    queries: captureIds.map((captureId) => ({
      queryKey: inboxKeys.capture(userId, captureId),
      queryFn: () => getCapture(captureId),
      placeholderData: processingById.get(captureId),
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
      refetchInterval: (query: { state: { data?: TextCapture } }) =>
        capturePollingInterval(query.state.data),
    })),
  });
}
