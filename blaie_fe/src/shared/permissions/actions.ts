export const permissionActions = [
  "capture.create",
  "capture.read",
  "capture.update",
  "capture.delete",
  "inbox.read",
  "item.read",
  "item.update",
  "item.delete",
  "task.read",
  "task.update",
  "calendar.read",
  "calendar.update",
  "reminder.read",
  "reminder.update",
  "information.read",
  "information.update",
  "admin.read",
  "admin.user.manage",
  "admin.job.manage",
] as const;

export type PermissionAction = (typeof permissionActions)[number];
