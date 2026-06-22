import type { PermissionAction } from "@/shared/permissions/actions";

export type UserSummary = {
  userId: string;
  tenantId: string | null;
  admin: boolean;
  permissions: PermissionAction[];
};
