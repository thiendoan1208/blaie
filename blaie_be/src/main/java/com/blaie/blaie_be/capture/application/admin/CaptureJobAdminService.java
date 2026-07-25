package com.blaie.blaie_be.capture.application.admin;

import com.blaie.blaie_be.capture.application.admin.query.AdminJobQuery;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import java.util.UUID;

public interface CaptureJobAdminService {
    AdminProcessingJobPageResult jobs(AdminJobQuery query);

    AdminProcessingJobResult job(UUID jobId);

    AdminProcessingJobResult requeue(UUID jobId);

    AdminProcessingJobResult markDead(UUID jobId, String reason);
}
