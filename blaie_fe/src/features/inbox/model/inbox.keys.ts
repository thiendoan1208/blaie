export const inboxKeys = {
  all: ["inbox"] as const,
  user: (userId: string) => [...inboxKeys.all, "user", userId] as const,
  list: (userId: string) => [...inboxKeys.user(userId), "list"] as const,
  processing: (userId: string) =>
    [...inboxKeys.user(userId), "processing"] as const,
  capture: (userId: string, captureId: string) =>
    [...inboxKeys.user(userId), "capture", captureId] as const,
};
