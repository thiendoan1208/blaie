import type {
  LoginInput,
  PasswordResetConfirmInput,
  PasswordResetRequestInput,
  RegisterInput,
} from "../model/auth.schema";

export type AuthUser = {
  id: string;
  username: string | null;
  email: string;
  emailVerified: boolean;
  hasPassword: boolean;
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

export type UpdateUsernameRequest = {
  username: string;
};

export type UpdatePasswordRequest = {
  currentPassword?: string;
  newPassword: string;
};

export type PasswordResetRequest = PasswordResetRequestInput;

export type PasswordResetConfirmRequest = Pick<
  PasswordResetConfirmInput,
  "email" | "code" | "newPassword"
>;
