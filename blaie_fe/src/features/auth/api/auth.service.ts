import { httpClient } from "@/shared/api/http-client";
import type { ApiResponse } from "@/shared/api/contracts/api-response";
import type {
  AuthUser,
  AuthUserEnvelope,
  LoginRequest,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  RegisterRequest,
  UpdatePasswordRequest,
  UpdateUsernameRequest,
} from "../types/types";

function unwrapUser(response: ApiResponse<AuthUserEnvelope>): AuthUser {
  return response.data.user;
}

export async function login(input: LoginRequest): Promise<AuthUser> {
  const response = await httpClient.post<ApiResponse<AuthUserEnvelope>>(
    "/auth/login",
    input,
  );
  return unwrapUser(response.data);
}

export async function register(input: RegisterRequest): Promise<AuthUser> {
  const response = await httpClient.post<ApiResponse<AuthUserEnvelope>>(
    "/auth/register",
    input,
  );
  return unwrapUser(response.data);
}

export async function getCurrentUser(): Promise<AuthUser> {
  const response =
    await httpClient.get<ApiResponse<AuthUserEnvelope>>("/auth/me");
  return unwrapUser(response.data);
}

export async function refreshSession(): Promise<AuthUser> {
  const response =
    await httpClient.post<ApiResponse<AuthUserEnvelope>>("/auth/refresh");
  return unwrapUser(response.data);
}

export async function updateUsername(input: UpdateUsernameRequest): Promise<AuthUser> {
  const response = await httpClient.patch<ApiResponse<AuthUserEnvelope>>(
    "/auth/me/username",
    input,
  );
  return unwrapUser(response.data);
}

export async function updatePassword(input: UpdatePasswordRequest): Promise<AuthUser> {
  const response = await httpClient.patch<ApiResponse<AuthUserEnvelope>>(
    "/auth/me/password",
    input,
  );
  return unwrapUser(response.data);
}

export async function logout(): Promise<void> {
  await httpClient.post("/auth/logout");
}

export async function resendEmailVerification(): Promise<void> {
  await httpClient.post("/auth/email/verification");
}

export async function requestPasswordReset(input: PasswordResetRequest): Promise<void> {
  await httpClient.post("/auth/password-reset/request", input);
}

export async function confirmPasswordReset(input: PasswordResetConfirmRequest): Promise<void> {
  await httpClient.post("/auth/password-reset/confirm", input);
}
