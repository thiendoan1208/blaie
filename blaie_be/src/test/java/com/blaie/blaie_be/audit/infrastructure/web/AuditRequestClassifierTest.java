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

    private AuditAccess classify(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return classifier.classify(request).orElseThrow();
    }
}
