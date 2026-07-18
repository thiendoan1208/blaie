package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.CaptureAdminStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.application.result.AdminOutboxSummaryResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CaptureAdminServiceImpl implements CaptureAdminService {
    private static final Logger log = LoggerFactory.getLogger(CaptureAdminServiceImpl.class);
    private static final int MAX_LIMIT = 100;
    private static final String JOB_CURSOR_AUDIENCE = "admin-processing-jobs";

    private final CaptureAdminStorePort store;
    private final CaptureProcessingSettingsPort settings;
    private final CaptureTelemetryPort telemetry;
    private final AuthorizationService authorizationService;
    private final Clock clock;
    private final SignedCursorCodec cursorCodec;

    public CaptureAdminServiceImpl(
            CaptureAdminStorePort store,
            CaptureProcessingSettingsPort settings,
            CaptureTelemetryPort telemetry,
            AuthorizationService authorizationService,
            Clock clock,
            SignedCursorCodec cursorCodec
    ) {
        this.store = store;
        this.settings = settings;
        this.telemetry = telemetry;
        this.authorizationService = authorizationService;
        this.clock = clock;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public AdminProcessingJobPageResult jobs(String status, String stuck, String cursor, String limit) {
        authorizationService.require(PermissionAction.ADMIN_READ);
        int safeLimit = requireLimit(limit);
        ProcessingJobStatus jobStatus = requireStatus(status);
        boolean stuckOnly = requireBoolean(stuck, "stuck");
        AdminProcessingJobCursor decodedCursor = decodeCursor(cursor);
        List<AdminProcessingJobResult> records = store.findJobs(
                jobStatus,
                stuckOnly,
                decodedCursor,
                clock.instant(),
                safeLimit + 1
        );
        boolean hasMore = records.size() > safeLimit;
        List<AdminProcessingJobResult> jobs = hasMore ? records.subList(0, safeLimit) : records;
        String nextCursor = hasMore ? encodeCursor(jobs.getLast()) : null;
        return new AdminProcessingJobPageResult(jobs, nextCursor, hasMore, safeLimit);
    }

    @Override
    public AdminProcessingJobResult job(String jobId) {
        authorizationService.require(PermissionAction.ADMIN_READ);
        return store.findJob(requireJobId(jobId))
                .orElseThrow(() -> new AppException(ErrorCode.PROCESSING_JOB_NOT_FOUND));
    }

    @Override
    public AdminProcessingJobResult requeue(String jobId) {
        authorizationService.require(PermissionAction.ADMIN_JOB_MANAGE);
        UUID parsedJobId = requireJobId(jobId);
        requireAsyncAcceptance();
        AdminProcessingJobMutationResult mutation = store.requeue(parsedJobId, clock.instant());
        AdminProcessingJobResult job = mutation.job();
        telemetry.incrementRetry(RetrySource.MANUAL);
        logOperation("requeue", parsedJobId, mutation.previousStatus(), job.status());
        return job;
    }

    @Override
    public AdminProcessingJobResult markDead(String jobId) {
        authorizationService.require(PermissionAction.ADMIN_JOB_MANAGE);
        UUID parsedJobId = requireJobId(jobId);
        AdminProcessingJobMutationResult mutation = store.markDead(parsedJobId, clock.instant());
        AdminProcessingJobResult job = mutation.job();
        telemetry.incrementDead(DeadSource.OPERATOR, TextClassificationFailureClass.SYSTEM_RETRYABLE);
        logOperation("mark_dead", parsedJobId, mutation.previousStatus(), job.status());
        return job;
    }

    @Override
    public AdminOutboxSummaryResult outboxSummary() {
        authorizationService.require(PermissionAction.ADMIN_READ);
        return store.outboxSummary(clock.instant());
    }

    private void requireAsyncAcceptance() {
        if (!settings.acceptAsyncEnabled()) {
            throw new AppException(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
        }
    }

    private int requireLimit(String value) {
        final int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw validation("limit must be an integer between 1 and " + MAX_LIMIT);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "limit must be between 1 and " + MAX_LIMIT
            );
        }
        return limit;
    }

    private ProcessingJobStatus requireStatus(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw validation("status is invalid");
        }
        try {
            return ProcessingJobStatus.fromValue(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("status is invalid");
        }
    }

    private UUID requireJobId(String value) {
        if (value == null || value.isBlank()) {
            throw validation("jobId is invalid");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw validation("jobId is invalid");
        }
    }

    private boolean requireBoolean(String value, String field) {
        if (value == null || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw validation(field + " must be true or false");
    }

    private AdminProcessingJobCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = cursorCodec.decode(JOB_CURSOR_AUDIENCE, cursor);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new AdminProcessingJobCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw validation("cursor is invalid");
        }
    }

    private String encodeCursor(AdminProcessingJobResult job) {
        String value = job.createdAt() + "|" + job.id();
        return cursorCodec.encode(JOB_CURSOR_AUDIENCE, value);
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private void logOperation(
            String action,
            UUID jobId,
            ProcessingJobStatus previousStatus,
            ProcessingJobStatus newStatus
    ) {
        log.warn(
                "Admin capture job operation: actorUserId={}, requestId={}, action={}, jobId={}, fromState={}, toState={}",
                CurrentUserHolder.requireCurrentUser().userId(),
                RequestContextHolder.currentRequestId().orElse("background"),
                action,
                jobId,
                previousStatus.value(),
                newStatus.value()
        );
    }
}
