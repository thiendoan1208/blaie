import axios from "axios";
import type { ApiErrorResponse } from "../contracts/api-response";
import { createAppError, isAppError, type AppError, type AppErrorCode } from "./app-error";

function requestIdFromHeaders(headers: unknown): string | undefined {
  if (!headers || typeof headers !== "object") {
    return undefined;
  }

  if ("get" in headers && typeof headers.get === "function") {
    const value = headers.get("x-request-id");
    return typeof value === "string" && value.length > 0 ? value : undefined;
  }

  const record = headers as Record<string, unknown>;
  const value = record["x-request-id"] ?? record["X-Request-ID"];
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function headerFrom(headers: unknown, name: string): string | undefined {
  if (!headers || typeof headers !== "object") {
    return undefined;
  }

  if ("get" in headers && typeof headers.get === "function") {
    const value = headers.get(name);
    return typeof value === "string" && value.length > 0 ? value : undefined;
  }

  const record = headers as Record<string, unknown>;
  const entry = Object.entries(record).find(
    ([key]) => key.toLowerCase() === name.toLowerCase(),
  );
  return typeof entry?.[1] === "string" && entry[1].length > 0
    ? entry[1]
    : undefined;
}

function retryAfterSecondsFromHeaders(headers: unknown): number | undefined {
  const value = headerFrom(headers, "retry-after")?.trim();
  if (!value) return undefined;

  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.ceil(seconds);
  }

  const retryAt = Date.parse(value);
  if (Number.isNaN(retryAt)) return undefined;
  return Math.max(0, Math.ceil((retryAt - Date.now()) / 1_000));
}

function fallbackCodeForStatus(status: number): AppErrorCode {
  if (status === 400) return "BAD_REQUEST";
  if (status === 401) return "UNAUTHORIZED";
  if (status === 403) return "FORBIDDEN";
  if (status === 404) return "NOT_FOUND";
  if (status === 409) return "CONFLICT";
  if (status === 422) return "VALIDATION_ERROR";
  return status === 0 ? "NETWORK_ERROR" : "HTTP_ERROR";
}

export function normalizeError(error: unknown): AppError {
  if (isAppError(error)) {
    return error;
  }

  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    if (error.code === "ECONNABORTED") {
      return createAppError({
        code: "TIMEOUT",
        status: 0,
        message: "Request timed out",
        cause: error,
      });
    }

    const status = error.response?.status ?? 0;
    const body = error.response?.data;
    const requestId = body?.requestId ?? requestIdFromHeaders(error.response?.headers);
    const retryAfterSeconds = retryAfterSecondsFromHeaders(
      error.response?.headers,
    );

    if (body?.code && body?.message) {
      return createAppError({
        code: body.code,
        status,
        message: body.message,
        fieldErrors: body.errors,
        requestId,
        retryAfterSeconds,
        cause: error,
      });
    }

    return createAppError({
      code: fallbackCodeForStatus(status),
      status,
      message: body?.message ?? error.message,
      requestId,
      retryAfterSeconds,
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
