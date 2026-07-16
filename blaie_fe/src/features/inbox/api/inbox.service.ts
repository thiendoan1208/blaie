import { httpClient } from "@/shared/api/http-client";
import type { ApiResponse } from "@/shared/api/contracts/api-response";

import type {
  CreateTextCaptureInput,
  InboxItem,
  TextCapture,
} from "../types/inbox";

export async function createTextCapture(
  input: CreateTextCaptureInput,
): Promise<TextCapture> {
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    "/captures/text",
    input,
  );
  return response.data.data;
}

export async function getInboxItems(): Promise<InboxItem[]> {
  const response = await httpClient.get<ApiResponse<InboxItem[]>>("/inbox", {
    params: { limit: 20 },
  });
  return response.data.data;
}
