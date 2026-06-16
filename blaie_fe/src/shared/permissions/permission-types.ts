import type { PermissionAction } from "./actions";

export type PermissionSubject = {
  admin: boolean;
  permissions: PermissionAction[];
};
