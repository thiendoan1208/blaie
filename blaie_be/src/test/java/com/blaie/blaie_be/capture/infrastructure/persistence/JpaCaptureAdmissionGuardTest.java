package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.error.RateLimitedException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class JpaCaptureAdmissionGuardTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private ProcessingJobRepository repository;
    private CaptureProcessingSettingsPort settings;
    private JpaCaptureAdmissionGuard guard;

    @BeforeEach
    void setUp() {
        repository = mock(ProcessingJobRepository.class);
        settings = mock(CaptureProcessingSettingsPort.class);
        when(settings.maxActiveJobsPerUser()).thenReturn(2);
        when(settings.maxActiveJobsTotal()).thenReturn(3);
        when(settings.maxOldestQueuedAge()).thenReturn(Duration.ofMinutes(1));
        when(settings.admissionRetryAfter()).thenReturn(RETRY_AFTER);
        guard = new JpaCaptureAdmissionGuard(repository, settings);
    }

    @Test
    void acquiresTheSingletonDatabaseMutex() {
        when(repository.acquireCaptureAdmissionMutex()).thenReturn(1);

        assertThatCode(guard::acquireGlobalMutex).doesNotThrowAnyException();

        verify(repository).acquireCaptureAdmissionMutex();
    }

    @Test
    void rejectsWhenTheUserAlreadyHasTheConfiguredActiveLimit() {
        UUID userId = UUID.randomUUID();
        when(repository.countActiveForUserUpTo(userId, 2)).thenReturn(2L);

        assertAdmissionError(
                () -> guard.requireCapacity(userId, NOW),
                ErrorCode.TOO_MANY_ACTIVE_JOBS
        );

        verify(repository).countActiveForUserUpTo(userId, 2);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsWhenTheGlobalActiveLimitIsReached() {
        UUID userId = UUID.randomUUID();
        when(repository.countActiveForUserUpTo(userId, 2)).thenReturn(1L);
        when(repository.countActiveUpTo(3)).thenReturn(3L);

        assertAdmissionError(
                () -> guard.requireCapacity(userId, NOW),
                ErrorCode.CAPTURE_PROCESSING_OVERLOADED
        );

        verify(repository).countActiveForUserUpTo(userId, 2);
        verify(repository).countActiveUpTo(3);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsWhenTheOldestQueuedJobExceedsTheAgeBudget() {
        UUID userId = UUID.randomUUID();
        Instant cutoff = NOW.minus(Duration.ofMinutes(1));
        when(repository.countActiveForUserUpTo(userId, 2)).thenReturn(1L);
        when(repository.countActiveUpTo(3)).thenReturn(1L);
        when(repository.existsQueuedOlderThan(cutoff)).thenReturn(true);

        assertAdmissionError(
                () -> guard.requireCapacity(userId, NOW),
                ErrorCode.CAPTURE_PROCESSING_OVERLOADED
        );

        verify(repository).existsQueuedOlderThan(cutoff);
    }

    @Test
    void permitsWorkBelowBothLimitsWithAHealthyQueueAge() {
        UUID userId = UUID.randomUUID();
        Instant cutoff = NOW.minus(Duration.ofMinutes(1));
        when(repository.countActiveForUserUpTo(userId, 2)).thenReturn(1L);
        when(repository.countActiveUpTo(3)).thenReturn(2L);
        when(repository.existsQueuedOlderThan(cutoff)).thenReturn(false);

        assertThatCode(() -> guard.requireCapacity(userId, NOW)).doesNotThrowAnyException();

        verify(repository).existsQueuedOlderThan(cutoff);
    }

    private void assertAdmissionError(Runnable operation, ErrorCode expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RateLimitedException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(expectedCode);
                    assertThat(exception.retryAfter()).isEqualTo(RETRY_AFTER);
                });
    }
}
