package com.blaie.blaie_be.capture.application.admin;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobCommandPort;
import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobQueryPort;
import com.blaie.blaie_be.capture.application.admin.query.AdminJobQuery;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import com.blaie.blaie_be.core.cursor.CursorProperties;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.request.RequestContext;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureJobAdminServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-17T15:00:00Z");
    private static final String REQUEST_ID = "request-admin-test";

    private AdminProcessingJobQueryPort jobQuery;
    private AdminProcessingJobCommandPort jobCommand;
    private CaptureProcessingSettingsPort settings;
    private CaptureTelemetryPort telemetry;
    private AuthorizationService authorization;
    private CaptureJobAdminServiceImpl service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        jobQuery = mock(AdminProcessingJobQueryPort.class);
        jobCommand = mock(AdminProcessingJobCommandPort.class);
        settings = mock(CaptureProcessingSettingsPort.class);
        telemetry = mock(CaptureTelemetryPort.class);
        authorization = mock(AuthorizationService.class);
        service = new CaptureJobAdminServiceImpl(
                jobQuery,
                jobCommand,
                settings,
                telemetry,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC),
                cursorCodec()
        );
        actorId = UUID.randomUUID();
        CurrentUserHolder.set(new CurrentUser(actorId.toString(), true, Set.of()));
        RequestContextHolder.set(new RequestContext(REQUEST_ID, "POST", "/admin", null));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
        RequestContextHolder.clear();
    }

    @Test
    void jobsBuildsAStableKeysetPageFromTypedInput() {
        AdminProcessingJobResult first = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(1));
        AdminProcessingJobResult second = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(2));
        AdminProcessingJobResult lookahead = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(3));
        when(jobQuery.findJobs(eq(ProcessingJobStatus.DEAD), eq(true), any(), eq(NOW), eq(3)))
                .thenReturn(List.of(first, second, lookahead));

        AdminProcessingJobPageResult page = service.jobs(
                new AdminJobQuery(ProcessingJobStatus.DEAD, true, null, 2)
        );

        assertThat(page.jobs()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        verify(authorization).require(PermissionAction.ADMIN_CAPTURE_JOB_READ);
    }

    @Test
    void invalidCursorFailsBeforePersistence() {
        assertValidation(() -> service.jobs(
                new AdminJobQuery(null, false, "not-a-signed-cursor", 20)
        ));
        verify(jobQuery, never()).findJobs(any(), eq(false), any(), any(), eq(20));
    }

    @Test
    void successfulAdminRequeueRecordsAuditContextAndTelemetry() {
        UUID jobId = UUID.randomUUID();
        AdminProcessingJobResult queued = job(ProcessingJobStatus.QUEUED, NOW);
        when(settings.acceptAsyncEnabled()).thenReturn(true);
        when(jobCommand.requeue(jobId, actorId, REQUEST_ID, NOW)).thenReturn(
                new AdminProcessingJobMutationResult(ProcessingJobStatus.DEAD, queued)
        );

        assertThat(service.requeue(jobId)).isEqualTo(queued);

        verify(authorization).require(PermissionAction.ADMIN_CAPTURE_JOB_MANAGE);
        verify(telemetry).incrementRetry(RetrySource.ADMIN);
    }

    @Test
    void failedAdminRequeueDoesNotRecordTelemetry() {
        UUID jobId = UUID.randomUUID();
        when(settings.acceptAsyncEnabled()).thenReturn(true);
        when(jobCommand.requeue(jobId, actorId, REQUEST_ID, NOW))
                .thenThrow(new AppException(ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED));

        assertThatThrownBy(() -> service.requeue(jobId)).isInstanceOf(AppException.class);

        verify(telemetry, never()).incrementRetry(any());
    }

    @Test
    void disabledAcceptanceRejectsRequeueBeforePersistence() {
        when(settings.acceptAsyncEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.requeue(UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE));

        verify(jobCommand, never()).requeue(any(), any(), any(), any());
    }

    @Test
    void successfulOperatorMarkDeadRequiresReasonAndRecordsContext() {
        UUID jobId = UUID.randomUUID();
        AdminProcessingJobResult dead = job(ProcessingJobStatus.DEAD, NOW);
        when(jobCommand.markDead(jobId, "operator decision", actorId, REQUEST_ID, NOW)).thenReturn(
                new AdminProcessingJobMutationResult(ProcessingJobStatus.PROCESSING, dead)
        );

        assertThat(service.markDead(jobId, " operator decision ")).isEqualTo(dead);

        verify(authorization).require(PermissionAction.ADMIN_CAPTURE_JOB_MANAGE);
        verify(telemetry).incrementDead(
                DeadSource.OPERATOR,
                CaptureFailureClass.SYSTEM_RETRYABLE
        );
    }

    @Test
    void markDeadRejectsBlankOrOversizedReasonBeforePersistence() {
        assertValidation(() -> service.markDead(UUID.randomUUID(), " "));
        assertValidation(() -> service.markDead(UUID.randomUUID(), "x".repeat(501)));
        verify(jobCommand, never()).markDead(any(), any(), any(), any(), any());
    }

    private SignedCursorCodec cursorCodec() {
        CursorProperties properties = new CursorProperties();
        properties.setActiveKeyId("v1");
        properties.setActiveSecret("capture-admin-test-cursor-secret-1234567890");
        return new SignedCursorCodec(properties);
    }

    private AdminProcessingJobResult job(ProcessingJobStatus status, Instant createdAt) {
        CaptureFailureClass failureClass = status == ProcessingJobStatus.DEAD
                ? CaptureFailureClass.SYSTEM_RETRYABLE
                : null;
        return new AdminProcessingJobResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "text_classification",
                status,
                status == ProcessingJobStatus.QUEUED ? 0 : 4,
                4,
                status == ProcessingJobStatus.QUEUED ? 1 : 0,
                2,
                REQUEST_ID,
                NOW,
                null,
                status == ProcessingJobStatus.DEAD ? "operator_marked_dead" : null,
                failureClass,
                status == ProcessingJobStatus.DEAD,
                NOW.minusSeconds(10),
                status == ProcessingJobStatus.QUEUED ? NOW.plusSeconds(30) : null,
                createdAt,
                NOW,
                status == ProcessingJobStatus.DEAD ? NOW : null
        );
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
