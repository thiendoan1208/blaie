export type AppErrorCode =
  | "BAD_REQUEST"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "VALIDATION_ERROR"
  | "CONFLICT"
  | "INVALID_CREDENTIALS"
  | "USERNAME_ALREADY_EXISTS"
  | "EMAIL_ALREADY_EXISTS"
  | "SESSION_EXPIRED"
  | "SESSION_REVOKED"
  | "TIMEOUT"
  | "NETWORK_ERROR"
  | "HTTP_ERROR"
  | "UNKNOWN_ERROR"
  | (string & {});

type AppErrorOptions = {
  code: AppErrorCode;
  status: number;
  message: string;
  fieldErrors?: Record<string, string[]>;
  requestId?: string;
  retryAfterSeconds?: number;
  cause?: unknown;
};

export class AppError extends Error {
  readonly code: AppErrorCode;
  readonly status: number;
  readonly fieldErrors?: Record<string, string[]>;
  readonly requestId?: string;
  readonly retryAfterSeconds?: number;
  override readonly cause?: unknown;

  constructor({
    code,
    status,
    message,
    fieldErrors,
    requestId,
    retryAfterSeconds,
    cause,
  }: AppErrorOptions) {
    super(message);
    this.name = "AppError";
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.requestId = requestId;
    this.retryAfterSeconds = retryAfterSeconds;
    this.cause = cause;
  }
}

export function createAppError(error: AppErrorOptions): AppError {
  return new AppError(error);
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}
