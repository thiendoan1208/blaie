export const authKeys = {
  all: ["auth"] as const,
  session: () => [...authKeys.all, "session"] as const,
  currentUser: () => [...authKeys.all, "current-user"] as const,
};
