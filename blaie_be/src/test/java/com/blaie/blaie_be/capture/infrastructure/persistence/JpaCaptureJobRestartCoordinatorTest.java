package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureJobDispatchPort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaCaptureJobRestartCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private CaptureItemRepository itemRepository;
    private CaptureRepository captureRepository;
    private ProcessingJobRepository jobRepository;
    private JpaCaptureAdmissionGuard admissionGuard;
    private CaptureProcessingSettingsPort settings;
    private CaptureJobDispatchPort jobDispatch;
    private JpaCaptureJobRestartCoordinator coordinator;

    @BeforeEach
    void setUp() {
        itemRepository = mock(CaptureItemRepository.class);
        captureRepository = mock(CaptureRepository.class);
        jobRepository = mock(ProcessingJobRepository.class);
        admissionGuard = mock(JpaCaptureAdmissionGuard.class);
        settings = mock(CaptureProcessingSettingsPort.class);
        jobDispatch = mock(CaptureJobDispatchPort.class);
        coordinator = new JpaCaptureJobRestartCoordinator(
                itemRepository,
                captureRepository,
                jobRepository,
                admissionGuard,
                settings,
                jobDispatch
        );
    }

    @Test
    void restartAppliesTheSingleSharedTransitionAndPublishesDispatch() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "text");
        ProcessingJobEntity job = ProcessingJobEntity.queued(
                capture,
                4,
                "request-restart-test",
                NOW.minusSeconds(20),
                NOW.minusSeconds(10)
        );
        job.dead("provider_retryable", CaptureFailureClass.PROVIDER_RETRYABLE, NOW.minusSeconds(5));
        capture.fail("provider_retryable");
        when(settings.dispatchRetryDelay(2)).thenReturn(Duration.ofSeconds(30));

        coordinator.restart(job, capture, NOW, ErrorCode.CAPTURE_NOT_RETRYABLE);

        assertThat(job.status()).isEqualTo("queued");
        assertThat(job.retryGeneration()).isEqualTo(1);
        assertThat(job.dispatchGeneration()).isEqualTo(2);
        assertThat(capture.processingStatus()).isEqualTo("processing");
        verify(admissionGuard).acquireGlobalMutex();
        verify(admissionGuard).requireCapacity(job.userId(), NOW);
        verify(itemRepository).deleteByCaptureId(capture.id());
        verify(captureRepository).flush();
        verify(jobRepository).flush();
        verify(jobDispatch).publish(
                job.id(),
                capture.id(),
                job.dispatchGeneration(),
                job.originRequestId()
        );
    }

    @Test
    void invalidStateIsRejectedBeforeAnyMutationOrCapacityCheck() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "text");
        ProcessingJobEntity job = ProcessingJobEntity.queued(
                capture,
                4,
                "request-restart-invalid",
                NOW,
                NOW
        );

        assertThatThrownBy(() -> coordinator.restart(
                job,
                capture,
                NOW,
                ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED));

        verify(admissionGuard, never()).acquireGlobalMutex();
        verify(itemRepository, never()).deleteByCaptureId(any());
        verify(jobDispatch, never()).publish(any(), any(), anyInt(), any());
    }
}
