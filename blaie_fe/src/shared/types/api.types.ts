import type { ApiErrorResponse, ApiResponse, CursorPageMeta } from "@/shared/api/api-response";

export type { ApiErrorResponse, ApiResponse, CursorPageMeta };

export type ApiCursorResponse<T> = ApiResponse<T> & {
  meta: CursorPageMeta;
};
