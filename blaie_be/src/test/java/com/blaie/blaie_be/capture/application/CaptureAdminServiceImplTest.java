package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.CaptureAdminStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.cursor.CursorProperties;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
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

class CaptureAdminServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-17T15:00:00Z");

    private CaptureAdminStorePort store;
    private CaptureProcessingSettingsPort settings;
    private CaptureTelemetryPort telemetry;
    private AuthorizationService authorization;
    private CaptureAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        store = mock(CaptureAdminStorePort.class);
        settings = mock(CaptureProcessingSettingsPort.class);
        telemetry = mock(CaptureTelemetryPort.class);
        authorization = mock(AuthorizationService.class);
        service = new CaptureAdminServiceImpl(
                store,
                settings,
                telemetry,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC),
                cursorCodec()
        );
        CurrentUserHolder.set(new CurrentUser(UUID.randomUUID().toString(), true, Set.of()));
    }

    private SignedCursorCodec cursorCodec() {
        CursorProperties properties = new CursorProperties();
        properties.setActiveKeyId("v1");
        properties.setActiveSecret("capture-admin-test-cursor-secret-1234567890");
        return new SignedCursorCodec(properties);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void jobsValidatesFiltersAndBuildsAStableKeysetPage() {
        AdminProcessingJobResult first = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(1));
        AdminProcessingJobResult second = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(2));
        AdminProcessingJobResult lookahead = job(ProcessingJobStatus.DEAD, NOW.minusSeconds(3));
        when(store.findJobs(eq(ProcessingJobStatus.DEAD), eq(true), any(), eq(NOW), eq(3)))
                .thenReturn(List.of(first, second, lookahead));

        AdminProcessingJobPageResult page = service.jobs("DEAD", "true", null, "2");

        assertThat(page.jobs()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        verify(authorization).require(PermissionAction.ADMIN_READ);
    }

    @Test
    void invalidAdminListInputsFailBeforePersistence() {
        assertValidation(() -> service.jobs("pending", "false", null, "20"));
        assertValidation(() -> service.jobs(null, "maybe", null, "20"));
        assertValidation(() -> service.jobs(null, "false", "not-base64", "20"));
        assertValidation(() -> service.jobs(null, "false", null, "101"));
        assertValidation(() -> service.jobs(null, "false", null, "abc"));
        verify(store, never()).findJobs(any(), eq(false), any(), any(), eq(20));
    }

    @Test
    void successfulAdminRequeueRecordsOneManualRetry() {
        UUID jobId = UUID.randomUUID();
        AdminProcessingJobResult queued = job(ProcessingJobStatus.QUEUED, NOW);
        when(settings.acceptAsyncEnabled()).thenReturn(true);
        when(store.requeue(jobId, NOW)).thenReturn(new AdminProcessingJobMutationResult(
                ProcessingJobStatus.DEAD,
                queued
        ));

        assertThat(service.requeue(jobId.toString())).isEqualTo(queued);

        verify(authorization).require(PermissionAction.ADMIN_JOB_MANAGE);
        verify(telemetry).incrementRetry(RetrySource.MANUAL);
    }

    @Test
    void failedAdminRequeueDoesNotRecordTelemetry() {
        UUID jobId = UUID.randomUUID();
        when(settings.acceptAsyncEnabled()).thenReturn(true);
        when(store.requeue(jobId, NOW))
                .thenThrow(new AppException(ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED));

        assertThatThrownBy(() -> service.requeue(jobId.toString()))
                .isInstanceOf(AppException.class);

        verify(telemetry, never()).incrementRetry(any());
    }

    @Test
    void disabledAcceptanceRejectsRequeueBeforePersistence() {
        when(settings.acceptAsyncEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.requeue(UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE));

        verify(store, never()).requeue(any(), any());
    }

    @Test
    void successfulOperatorMarkDeadUsesBoundedTelemetryLabels() {
        UUID jobId = UUID.randomUUID();
        AdminProcessingJobResult dead = job(ProcessingJobStatus.DEAD, NOW);
        when(store.markDead(jobId, NOW)).thenReturn(new AdminProcessingJobMutationResult(
                ProcessingJobStatus.PROCESSING,
                dead
        ));

        assertThat(service.markDead(jobId.toString())).isEqualTo(dead);

        verify(authorization).require(PermissionAction.ADMIN_JOB_MANAGE);
        verify(telemetry).incrementDead(
                DeadSource.OPERATOR,
                TextClassificationFailureClass.SYSTEM_RETRYABLE
        );
    }

    private AdminProcessingJobResult job(ProcessingJobStatus status, Instant createdAt) {
        TextClassificationFailureClass failureClass = status == ProcessingJobStatus.DEAD
                ? TextClassificationFailureClass.SYSTEM_RETRYABLE
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
                "request-admin-test",
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
