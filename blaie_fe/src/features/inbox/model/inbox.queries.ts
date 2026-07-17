import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import {
  getCapture,
  getInboxItems,
  getProcessingCaptures,
} from "../api/inbox.service";
import { inboxKeys } from "./inbox.keys";

export function useInboxItemsQuery() {
  return useQuery({
    queryKey: inboxKeys.list(),
    queryFn: getInboxItems,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  });
}

export function useCaptureQuery(captureId: string | null) {
  return useQuery({
    queryKey: inboxKeys.capture(captureId ?? "none"),
    queryFn: () => getCapture(captureId!),
    enabled: captureId !== null,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: (query) =>
      query.state.data?.processingStatus === "processing" ? 1_500 : false,
  });
}

export function useProcessingCapturesQuery() {
  const queryClient = useQueryClient();
  const previousIds = useRef<Set<string>>(new Set());
  const query = useQuery({
    queryKey: inboxKeys.processing(),
    queryFn: getProcessingCaptures,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: (currentQuery) =>
      currentQuery.state.data?.length ? 2_000 : false,
  });

  useEffect(() => {
    if (!query.data) return;
    const currentIds = new Set(query.data.map((capture) => capture.id));
    const finished = [...previousIds.current].some((id) => !currentIds.has(id));
    previousIds.current = currentIds;
    if (finished) {
      void queryClient.invalidateQueries({ queryKey: inboxKeys.list() });
    }
  }, [query.data, queryClient]);

  return query;
}
