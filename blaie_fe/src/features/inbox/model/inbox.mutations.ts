import { useMutation, useQueryClient } from "@tanstack/react-query";

import { isAppError } from "@/shared/api/errors/app-error";

import { createTextCapture, retryCapture } from "../api/inbox.service";
import { inboxKeys } from "./inbox.keys";

export function useCreateTextCaptureMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTextCapture,
    retry: (failureCount, error) =>
      isAppError(error) &&
      (error.status === 0 || error.status >= 500) &&
      failureCount < 2,
    retryDelay: (attempt) => Math.min(500 * 2 ** attempt, 2_000),
    onSuccess: async (capture) => {
      queryClient.setQueryData(inboxKeys.capture(capture.id), capture);
      await queryClient.invalidateQueries({ queryKey: inboxKeys.processing() });
    },
  });
}

export function useRetryCaptureMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: retryCapture,
    onSuccess: async (capture) => {
      queryClient.setQueryData(inboxKeys.capture(capture.id), capture);
      await queryClient.invalidateQueries({ queryKey: inboxKeys.processing() });
    },
  });
}
