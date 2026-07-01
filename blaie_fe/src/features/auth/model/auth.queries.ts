import { useQuery } from "@tanstack/react-query";
import { getCurrentUser } from "../api/auth.service";
import { authKeys } from "./auth.keys";

export function useCurrentUserQuery() {
  return useQuery({
    queryKey: authKeys.currentUser(),
    queryFn: getCurrentUser,
  });
}
