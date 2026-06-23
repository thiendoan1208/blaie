import type { LoginInput, RegisterInput } from "../model/auth.schema";

export type AuthUser = {
  id: string;
  username: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  createdAt: string;
};

export type AuthUserEnvelope = {
  user: AuthUser;
};

export type CsrfTokenResponse = {
  token: string;
  headerName: string;
};

export type LoginRequest = LoginInput;
export type RegisterRequest = RegisterInput;
