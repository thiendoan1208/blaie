package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.result.AdminOutboxSummaryResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;

public interface CaptureAdminService {
    AdminProcessingJobPageResult jobs(String status, String stuck, String cursor, String limit);

    AdminProcessingJobResult job(String jobId);

    AdminProcessingJobResult requeue(String jobId);

    AdminProcessingJobResult markDead(String jobId);

    AdminOutboxSummaryResult outboxSummary();
}
