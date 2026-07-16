import { useQuery } from "@tanstack/react-query";

import { getInboxItems } from "../api/inbox.service";
import { inboxKeys } from "./inbox.keys";

export function useInboxItemsQuery() {
  return useQuery({
    queryKey: inboxKeys.list(),
    queryFn: getInboxItems,
  });
}
