import axios from "axios";
import type { ApiErrorResponse } from "./api-response";
import { createAppError, type AppError } from "./app-error";

export function normalizeError(error: unknown): AppError {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const status = error.response?.status ?? 0;
    const body = error.response?.data;

    if (body?.code && body?.message) {
      return createAppError({
        code: body.code,
        status,
        message: body.message,
        fieldErrors: body.errors,
        requestId: body.requestId,
        cause: error,
      });
    }

    if (error.code === "ECONNABORTED") {
      return createAppError({
        code: "TIMEOUT",
        status: 0,
        message: "Request timed out",
        cause: error,
      });
    }

    return createAppError({
      code: status === 0 ? "NETWORK_ERROR" : "HTTP_ERROR",
      status,
      message: body?.message ?? error.message,
      requestId: body?.requestId,
      cause: error,
    });
  }

  if (error instanceof Error) {
    return createAppError({
      code: "UNKNOWN_ERROR",
      status: 0,
      message: error.message,
      cause: error,
    });
  }

  return createAppError({
    code: "UNKNOWN_ERROR",
    status: 0,
    message: "Unknown error",
    cause: error,
  });
}
