import type { AxiosInstance, AxiosResponse } from "axios";
import { tryHandleAuthRefreshError } from "../interceptors/auth-refresh";
import { normalizeError } from "./normalize-error";

export type HttpErrorHandlerResult =
  | { handled: true; response: AxiosResponse }
  | { handled: false };

export async function handleHttpError(error: unknown, client: AxiosInstance) {
  const authRefreshResult = await tryHandleAuthRefreshError(error, client);
  if (authRefreshResult.handled) {
    return authRefreshResult.response;
  }

  return Promise.reject(normalizeError(error));
}
