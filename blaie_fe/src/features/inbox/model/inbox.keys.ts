export const inboxKeys = {
  all: ["inbox"] as const,
  list: () => [...inboxKeys.all, "list"] as const,
  processing: () => [...inboxKeys.all, "processing"] as const,
  capture: (captureId: string) =>
    [...inboxKeys.all, "capture", captureId] as const,
};
