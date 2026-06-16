import type { CurrentUser } from "@/shared/auth/auth-types";
import type { PermissionAction } from "./actions";

export function can(user: CurrentUser | null | undefined, action: PermissionAction): boolean {
  if (!user) {
    return false;
  }

  if (user.admin) {
    return true;
  }

  return user.permissions.includes(action);
}
