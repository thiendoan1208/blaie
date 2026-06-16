import type { ApiErrorResponse, ApiResponse, PageMeta } from "@/shared/api/api-response";

export type { ApiErrorResponse, ApiResponse, PageMeta };

export type ApiPageResponse<T> = ApiResponse<T> & {
  meta: PageMeta;
};
