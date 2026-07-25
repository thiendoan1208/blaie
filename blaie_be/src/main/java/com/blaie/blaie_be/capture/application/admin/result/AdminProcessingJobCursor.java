package com.blaie.blaie_be.capture.application.admin.result;

import java.time.Instant;
import java.util.UUID;

public record AdminProcessingJobCursor(Instant createdAt, UUID jobId) {
}
