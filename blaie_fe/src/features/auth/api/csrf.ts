import { httpClient } from "@/shared/api/http-client";
import type { ApiResponse } from "@/shared/api/contracts/api-response";
import type { CsrfTokenResponse } from "../types/types";

let csrfBootstrapPromise: Promise<CsrfTokenResponse> | null = null;

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response =
    await httpClient.get<ApiResponse<CsrfTokenResponse>>("/auth/csrf");
  return response.data.data;
}

export function ensureCsrfToken(): Promise<CsrfTokenResponse> {
  csrfBootstrapPromise ??= getCsrfToken().catch((error: unknown) => {
    csrfBootstrapPromise = null;
    throw error;
  });

  return csrfBootstrapPromise;
}
