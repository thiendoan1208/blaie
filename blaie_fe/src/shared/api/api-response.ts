export type PageMeta = {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
};

export type ApiResponse<T> = {
  data: T;
  message?: string;
  meta?: PageMeta;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
  errors?: Record<string, string[]>;
  requestId?: string;
};
