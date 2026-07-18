import { useMutation, useQueryClient } from "@tanstack/react-query";

import { isAppError } from "@/shared/api/errors/app-error";

import {
  createTextCapture,
  deleteCapture,
  retryCapture,
} from "../api/inbox.service";
import { inboxKeys } from "./inbox.keys";

export function useCreateTextCaptureMutation(userId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTextCapture,
    retry: (failureCount, error) =>
      isAppError(error) &&
      (error.status === 0 || error.status >= 500) &&
      failureCount < 2,
    retryDelay: (attempt) => Math.min(500 * 2 ** attempt, 2_000),
    onSuccess: async (capture) => {
      queryClient.setQueryData(
        inboxKeys.capture(userId, capture.id),
        capture,
      );
      await queryClient.invalidateQueries({
        queryKey: inboxKeys.processing(userId),
      });
    },
  });
}

export function useRetryCaptureMutation(userId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: retryCapture,
    onSuccess: async (capture) => {
      queryClient.setQueryData(
        inboxKeys.capture(userId, capture.id),
        capture,
      );
      await queryClient.invalidateQueries({
        queryKey: inboxKeys.processing(userId),
      });
    },
  });
}

export function useDeleteCaptureMutation(userId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (captureId: string) => {
      try {
        await deleteCapture(captureId);
      } catch (error) {
        if (isAppError(error) && error.status === 404) return;
        throw error;
      }
    },
    onSuccess: async (_, captureId) => {
      queryClient.removeQueries({
        queryKey: inboxKeys.capture(userId, captureId),
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: inboxKeys.processing(userId) }),
        queryClient.invalidateQueries({ queryKey: inboxKeys.list(userId) }),
      ]);
    },
    onSettled: async (_, error, captureId) => {
      if (!error) return;
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: inboxKeys.capture(userId, captureId),
        }),
        queryClient.invalidateQueries({ queryKey: inboxKeys.processing(userId) }),
        queryClient.invalidateQueries({ queryKey: inboxKeys.list(userId) }),
      ]);
    },
  });
}
