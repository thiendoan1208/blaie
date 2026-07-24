import { httpClient } from "@/shared/api/http-client";
import { ensureCsrfCookie } from "@/shared/api/interceptors/csrf-header";
import type { ApiResponse } from "@/shared/api/contracts/api-response";
import type { CsrfTokenResponse } from "../types/types";

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response =
    await httpClient.get<ApiResponse<CsrfTokenResponse>>("/auth/csrf");
  return response.data.data;
}

export function ensureCsrfToken(): Promise<void> {
  return ensureCsrfCookie(httpClient);
}
