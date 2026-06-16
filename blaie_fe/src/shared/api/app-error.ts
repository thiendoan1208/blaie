export type AppError = {
  name: "AppError";
  code: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string[]>;
  requestId?: string;
  cause?: unknown;
};

export function createAppError(error: Omit<AppError, "name">): AppError {
  return {
    name: "AppError",
    ...error,
  };
}
