package com.blaie.blaie_be.capture.application.admin;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobCommandPort;
import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobQueryPort;
import com.blaie.blaie_be.capture.application.admin.query.AdminJobQuery;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CaptureJobAdminServiceImpl implements CaptureJobAdminService {
    private static final Logger log = LoggerFactory.getLogger(CaptureJobAdminServiceImpl.class);
    private static final String JOB_CURSOR_AUDIENCE = "admin-processing-jobs";

    private final AdminProcessingJobQueryPort jobQuery;
    private final AdminProcessingJobCommandPort jobCommand;
    private final CaptureProcessingSettingsPort settings;
    private final CaptureTelemetryPort telemetry;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final SignedCursorCodec cursorCodec;

    public CaptureJobAdminServiceImpl(
            AdminProcessingJobQueryPort jobQuery,
            AdminProcessingJobCommandPort jobCommand,
            CaptureProcessingSettingsPort settings,
            CaptureTelemetryPort telemetry,
            AuthorizationService authorization,
            Clock clock,
            SignedCursorCodec cursorCodec
    ) {
        this.jobQuery = jobQuery;
        this.jobCommand = jobCommand;
        this.settings = settings;
        this.telemetry = telemetry;
        this.authorization = authorization;
        this.clock = clock;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public AdminProcessingJobPageResult jobs(AdminJobQuery query) {
        authorization.require(PermissionAction.ADMIN_CAPTURE_JOB_READ);
        AdminProcessingJobCursor cursor = decodeCursor(query.cursor());
        List<AdminProcessingJobResult> records = jobQuery.findJobs(
                query.status(),
                query.stuck(),
                cursor,
                clock.instant(),
                query.limit() + 1
        );
        boolean hasMore = records.size() > query.limit();
        List<AdminProcessingJobResult> jobs = hasMore
                ? records.subList(0, query.limit())
                : records;
        return new AdminProcessingJobPageResult(
                jobs,
                hasMore ? encodeCursor(jobs.getLast()) : null,
                hasMore,
                query.limit()
        );
    }

    @Override
    public AdminProcessingJobResult job(UUID jobId) {
        authorization.require(PermissionAction.ADMIN_CAPTURE_JOB_READ);
        return jobQuery.findJob(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.PROCESSING_JOB_NOT_FOUND));
    }

    @Override
    public AdminProcessingJobResult requeue(UUID jobId) {
        authorization.require(PermissionAction.ADMIN_CAPTURE_JOB_MANAGE);
        if (!settings.acceptAsyncEnabled()) {
            throw new AppException(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
        }
        AdminOperationContext context = currentContext();
        AdminProcessingJobMutationResult mutation = jobCommand.requeue(
                jobId,
                context.actorId(),
                context.requestId(),
                clock.instant()
        );
        telemetry.incrementRetry(RetrySource.ADMIN);
        logOperation("requeue", mutation);
        return mutation.job();
    }

    @Override
    public AdminProcessingJobResult markDead(UUID jobId, String reason) {
        authorization.require(PermissionAction.ADMIN_CAPTURE_JOB_MANAGE);
        String safeReason = requireReason(reason);
        AdminOperationContext context = currentContext();
        AdminProcessingJobMutationResult mutation = jobCommand.markDead(
                jobId,
                safeReason,
                context.actorId(),
                context.requestId(),
                clock.instant()
        );
        telemetry.incrementDead(DeadSource.OPERATOR, CaptureFailureClass.SYSTEM_RETRYABLE);
        logOperation("mark_dead", mutation);
        return mutation.job();
    }

    private AdminProcessingJobCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = cursorCodec.decode(JOB_CURSOR_AUDIENCE, cursor).split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new AdminProcessingJobCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw validation("cursor is invalid");
        }
    }

    private String encodeCursor(AdminProcessingJobResult job) {
        return cursorCodec.encode(JOB_CURSOR_AUDIENCE, job.createdAt() + "|" + job.id());
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw validation("reason must not be blank");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw validation("reason must not exceed 500 characters");
        }
        return normalized;
    }

    private AdminOperationContext currentContext() {
        CurrentUser actor = CurrentUserHolder.requireCurrentUser();
        return new AdminOperationContext(
                UUID.fromString(actor.userId()),
                RequestContextHolder.currentRequestId().orElse("background")
        );
    }

    private void logOperation(String action, AdminProcessingJobMutationResult mutation) {
        log.warn(
                "Admin capture job operation: actorUserId={}, requestId={}, action={}, jobId={}, fromState={}, toState={}",
                CurrentUserHolder.requireCurrentUser().userId(),
                RequestContextHolder.currentRequestId().orElse("background"),
                action,
                mutation.job().id(),
                mutation.previousStatus().value(),
                mutation.job().status().value()
        );
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record AdminOperationContext(UUID actorId, String requestId) {
    }
}
