package com.blaie.blaie_be.authz.domain;

public enum PermissionAction {
    CAPTURE_CREATE("capture.create"),
    CAPTURE_READ("capture.read"),
    CAPTURE_UPDATE("capture.update"),
    CAPTURE_DELETE("capture.delete"),
    INBOX_READ("inbox.read"),
    ITEM_READ("item.read"),
    ITEM_UPDATE("item.update"),
    ITEM_DELETE("item.delete"),
    TASK_READ("task.read"),
    TASK_UPDATE("task.update"),
    CALENDAR_READ("calendar.read"),
    CALENDAR_UPDATE("calendar.update"),
    REMINDER_READ("reminder.read"),
    REMINDER_UPDATE("reminder.update"),
    INFORMATION_READ("information.read"),
    INFORMATION_UPDATE("information.update"),
    ADMIN_USER_MANAGE("admin.user.manage"),
    ADMIN_CAPTURE_JOB_READ("admin.capture.job.read"),
    ADMIN_CAPTURE_JOB_MANAGE("admin.capture.job.manage"),
    ADMIN_CAPTURE_OUTBOX_READ("admin.capture.outbox.read"),
    ADMIN_AUDIT_READ("admin.audit.read");

    private final String key;

    PermissionAction(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
