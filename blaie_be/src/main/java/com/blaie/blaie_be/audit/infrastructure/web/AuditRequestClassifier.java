package com.blaie.blaie_be.audit.infrastructure.web;

import com.blaie.blaie_be.audit.domain.AuditAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuditRequestClassifier {
    public Optional<AuditAccess> classify(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("GET".equals(method) && "/api/v1/captures".equals(path)) {
            return access("capture.list", "capture", null);
        }
        if ("GET".equals(method) && "/api/v1/captures/resolve".equals(path)) {
            return access("capture.resolve", "capture", null);
        }
        if (path.startsWith("/api/v1/captures/")) {
            String suffix = path.substring("/api/v1/captures/".length());
            if (suffix.endsWith("/retry") && "POST".equals(method)) {
                return access("capture.retry", "capture", stripSuffix(suffix, "/retry"));
            }
            if (!suffix.contains("/")) {
                if ("GET".equals(method)) return access("capture.read", "capture", suffix);
                if ("DELETE".equals(method)) return access("capture.delete", "capture", suffix);
            }
        }
        if ("GET".equals(method) && path.startsWith("/api/v1/inbox/items/")) {
            return access("inbox_item.read", "inbox_item", path.substring("/api/v1/inbox/items/".length()));
        }
        if ("GET".equals(method) && "/api/v1/inbox".equals(path)) {
            return access("inbox.list", "inbox", null);
        }
        if ("GET".equals(method) && "/api/v1/admin/jobs".equals(path)) {
            return access("admin.job.list", "processing_job", null);
        }
        if (path.startsWith("/api/v1/admin/jobs/")) {
            String suffix = path.substring("/api/v1/admin/jobs/".length());
            if (suffix.endsWith("/requeue") && "POST".equals(method)) {
                return access("admin.job.requeue", "processing_job", stripSuffix(suffix, "/requeue"));
            }
            if (suffix.endsWith("/mark-dead") && "POST".equals(method)) {
                return access("admin.job.mark_dead", "processing_job", stripSuffix(suffix, "/mark-dead"));
            }
            if ("GET".equals(method) && !suffix.contains("/")) {
                return access("admin.job.read", "processing_job", suffix);
            }
        }
        if ("GET".equals(method) && "/api/v1/admin/outbox/summary".equals(path)) {
            return access("admin.outbox.read", "outbox", null);
        }
        if ("GET".equals(method) && "/api/v1/admin/audit-events".equals(path)) {
            return access("admin.audit.list", "audit_event", null);
        }
        return Optional.empty();
    }

    private Optional<AuditAccess> access(String action, String resourceType, String resourceId) {
        try {
            return Optional.of(new AuditAccess(action, resourceType, resourceId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String stripSuffix(String value, String suffix) {
        return value.substring(0, value.length() - suffix.length());
    }
}
