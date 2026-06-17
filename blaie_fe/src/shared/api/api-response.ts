export type CursorPageMeta = {
  nextCursor: string | null;
  hasMore: boolean;
  limit: number;
};

export type ApiResponse<T> = {
  data: T;
  message?: string;
  meta?: CursorPageMeta;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
  errors?: Record<string, string[]>;
  requestId?: string;
};
