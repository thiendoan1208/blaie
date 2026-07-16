import { useMutation, useQueryClient } from "@tanstack/react-query";

import { createTextCapture } from "../api/inbox.service";
import { inboxKeys } from "./inbox.keys";

export function useCreateTextCaptureMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTextCapture,
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: inboxKeys.list() });
    },
  });
}
