import type { PermissionAction } from "@/shared/permissions/actions";

export type CurrentUser = {
  userId: string;
  tenantId: string | null;
  admin: boolean;
  permissions: PermissionAction[];
};

export type AuthSession = {
  accessToken?: string | null;
  refreshToken?: string | null;
  user?: CurrentUser | null;
};
