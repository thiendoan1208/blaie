import { httpClient } from "@/shared/api/http-client";
import type { ApiResponse } from "@/shared/api/contracts/api-response";

import type {
  CreateTextCaptureInput,
  CreateImageCaptureInput,
  InboxItem,
  InboxPage,
  TextCapture,
} from "../types/inbox";

function normalizeCapture(capture: TextCapture): TextCapture {
  const baseUrl = httpClient.defaults.baseURL;
  if (!baseUrl || !/^https?:\/\//i.test(baseUrl)) return capture;
  return {
    ...capture,
    attachments: capture.attachments.map((attachment) => ({
      ...attachment,
      contentUrl: /^https?:\/\//i.test(attachment.contentUrl)
        ? attachment.contentUrl
        : new URL(attachment.contentUrl, baseUrl).toString(),
    })),
  };
}

export async function createTextCapture(
  input: CreateTextCaptureInput,
): Promise<TextCapture> {
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    "/captures/text",
    { text: input.text },
    { headers: { "Idempotency-Key": input.idempotencyKey } },
  );
  return normalizeCapture(response.data.data);
}

export async function createImageCapture(
  input: CreateImageCaptureInput,
): Promise<TextCapture> {
  const form = new FormData();
  form.append("image", input.image, input.image.name);
  if (input.text?.trim()) form.append("text", input.text.trim());
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    "/captures/image",
    form,
    { headers: { "Idempotency-Key": input.idempotencyKey } },
  );
  return normalizeCapture(response.data.data);
}

export async function getCapture(captureId: string): Promise<TextCapture> {
  const response = await httpClient.get<ApiResponse<TextCapture>>(
    `/captures/${captureId}`,
  );
  return normalizeCapture(response.data.data);
}

export async function resolveCapture(
  idempotencyKey: string,
): Promise<TextCapture> {
  const response = await httpClient.get<ApiResponse<TextCapture>>(
    "/captures/resolve",
    { headers: { "Idempotency-Key": idempotencyKey } },
  );
  return normalizeCapture(response.data.data);
}

export async function getProcessingCaptures(): Promise<TextCapture[]> {
  const response = await httpClient.get<ApiResponse<TextCapture[]>>(
    "/captures",
    { params: { status: "processing", limit: 20 } },
  );
  return response.data.data.map(normalizeCapture);
}

export async function retryCapture(captureId: string): Promise<TextCapture> {
  const response = await httpClient.post<ApiResponse<TextCapture>>(
    `/captures/${captureId}/retry`,
  );
  return normalizeCapture(response.data.data);
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
