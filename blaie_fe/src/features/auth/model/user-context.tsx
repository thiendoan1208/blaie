"use client";

import {
  createContext,
  type ReactNode,
  useContext,
  useMemo,
} from "react";
import type { AuthUser } from "../types/types";
import { useCurrentUserQuery } from "./auth.queries";

type UserContextValue = {
  user: AuthUser | undefined;
  isPending: boolean;
  isError: boolean;
  refetchUser: () => Promise<unknown>;
};

const UserContext = createContext<UserContextValue | null>(null);

export function UserProvider({ children }: { children: ReactNode }) {
  const currentUserQuery = useCurrentUserQuery();
  const value = useMemo<UserContextValue>(
    () => ({
      user: currentUserQuery.data,
      isPending: currentUserQuery.isPending,
      isError: currentUserQuery.isError,
      refetchUser: currentUserQuery.refetch,
    }),
    [
      currentUserQuery.data,
      currentUserQuery.isError,
      currentUserQuery.isPending,
      currentUserQuery.refetch,
    ],
  );

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser() {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error("useUser must be used within UserProvider");
  }
  return context;
}
