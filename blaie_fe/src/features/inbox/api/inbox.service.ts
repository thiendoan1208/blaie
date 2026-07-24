import { httpClient } from "@/shared/api/http-client";
import type { ApiResponse } from "@/shared/api/contracts/api-response";

import type {
  CreateTextCaptureInput,
  InboxItem,
  InboxPage,
  TextCapture,
} from "../types/inbox";

export async function createTextCapture(
  input: CreateTextCaptureInput,
): Promise<TextCapture> {
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    "/captures/text",
    { text: input.text },
    { headers: { "Idempotency-Key": input.idempotencyKey } },
  );
  return response.data.data;
}

export async function getCapture(captureId: string): Promise<TextCapture> {
  const response = await httpClient.get<ApiResponse<TextCapture>>(
    `/captures/${captureId}`,
  );
  return response.data.data;
}

export async function resolveCapture(
  idempotencyKey: string,
): Promise<TextCapture> {
  const response = await httpClient.get<ApiResponse<TextCapture>>(
    "/captures/resolve",
    { headers: { "Idempotency-Key": idempotencyKey } },
  );
  return response.data.data;
}

export async function getProcessingCaptures(): Promise<TextCapture[]> {
  const response = await httpClient.get<ApiResponse<TextCapture[]>>(
    "/captures",
    { params: { status: "processing", limit: 20 } },
  );
  return response.data.data;
}

export async function retryCapture(captureId: string): Promise<TextCapture> {
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    `/captures/${captureId}/retry`,
  );
  return response.data.data;
}

export async function deleteCapture(captureId: string): Promise<void> {
  await httpClient.delete(`/captures/${captureId}`);
}

export async function getInboxItems({
  cursor,
  limit = 20,
}: {
  cursor: string | null;
  limit?: number;
}): Promise<InboxPage> {
  const response = await httpClient.get<ApiResponse<InboxItem[]>>("/inbox", {
    params: { cursor: cursor ?? undefined, limit },
  });
  const meta = response.data.meta;
  return {
    items: response.data.data,
    nextCursor: meta?.nextCursor ?? null,
    hasMore: meta?.hasMore ?? false,
    limit: meta?.limit ?? limit,
  };
}
