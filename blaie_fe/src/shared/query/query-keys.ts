export const queryKeys = {
  auth: {
    session: ["auth", "session"] as const,
    currentUser: ["auth", "current-user"] as const,
  },
  app: {
    inbox: ["app", "inbox"] as const,
    tasks: ["app", "tasks"] as const,
    notes: ["app", "notes"] as const,
  },
} as const;
