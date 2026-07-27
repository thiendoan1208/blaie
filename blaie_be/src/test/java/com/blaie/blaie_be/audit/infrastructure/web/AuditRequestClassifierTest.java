package com.blaie.blaie_be.audit.infrastructure.web;

import com.blaie.blaie_be.audit.domain.AuditAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestClassifierTest {
    private final AuditRequestClassifier classifier = new AuditRequestClassifier();

    @Test
    void classifiesCaptureOwnedAdminRoutes() {
        assertThat(classify("GET", "/api/v1/admin/capture/jobs"))
                .isEqualTo(new AuditAccess("admin.job.list", "processing_job", null));
        assertThat(classify("POST", "/api/v1/admin/capture/jobs/job-1/mark-dead"))
                .isEqualTo(new AuditAccess("admin.job.mark_dead", "processing_job", "job-1"));
        assertThat(classify("GET", "/api/v1/admin/capture/outbox/summary"))
                .isEqualTo(new AuditAccess("admin.outbox.read", "outbox", null));
    }

    @Test
    void oldAmbiguousAdminCaptureRoutesAreNoLongerClassified() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/jobs");
        assertThat(classifier.classify(request)).isEmpty();
    }

    @Test
    void classifiesEveryCurrentUserCaptureAndInboxOperation() {
        assertThat(classify("POST", "/api/v1/captures/text"))
                .isEqualTo(new AuditAccess("capture.create", "capture", null));
        assertThat(classify("POST", "/api/v1/transcriptions/audio"))
                .isEqualTo(new AuditAccess("capture.transcribe", "capture", null));
        assertThat(classify("GET", "/api/v1/captures"))
                .isEqualTo(new AuditAccess("capture.list", "capture", null));
        assertThat(classify("GET", "/api/v1/captures/resolve"))
                .isEqualTo(new AuditAccess("capture.resolve", "capture", null));
        assertThat(classify("GET", "/api/v1/captures/capture-1"))
                .isEqualTo(new AuditAccess("capture.read", "capture", "capture-1"));
        assertThat(classify("POST", "/api/v1/captures/capture-1/retry"))
                .isEqualTo(new AuditAccess("capture.retry", "capture", "capture-1"));
        assertThat(classify("DELETE", "/api/v1/captures/capture-1"))
                .isEqualTo(new AuditAccess("capture.delete", "capture", "capture-1"));
        assertThat(classify("GET", "/api/v1/inbox"))
                .isEqualTo(new AuditAccess("inbox.list", "inbox", null));
        assertThat(classify("GET", "/api/v1/inbox/items/item-1"))
                .isEqualTo(new AuditAccess("inbox_item.read", "inbox_item", "item-1"));
    }

    private AuditAccess classify(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return classifier.classify(request).orElseThrow();
    }
}
