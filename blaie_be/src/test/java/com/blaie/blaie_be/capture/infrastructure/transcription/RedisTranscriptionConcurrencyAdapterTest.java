package com.blaie.blaie_be.capture.infrastructure.transcription;

import com.blaie.blaie_be.capture.application.port.TranscriptionConcurrencyPort;
import com.blaie.blaie_be.capture.infrastructure.ai.ProviderConcurrencyLimiter;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTranscriptionConcurrencyAdapterTest {
    @Test
    void reusesTheDistributedProviderSemaphoreAndReleasesItsPermit() {
        ProviderConcurrencyLimiter limiter = mock(ProviderConcurrencyLimiter.class);
        ProviderConcurrencyLimiter.Permit providerPermit =
                mock(ProviderConcurrencyLimiter.Permit.class);
        when(limiter.acquire(RedisTranscriptionConcurrencyAdapter.PROVIDER_ID))
                .thenReturn(providerPermit);
        RedisTranscriptionConcurrencyAdapter adapter =
                new RedisTranscriptionConcurrencyAdapter(limiter);

        try (TranscriptionConcurrencyPort.Permit ignored = adapter.acquire()) {
            assertThat(ignored).isNotNull();
        }

        verify(providerPermit).close();
    }

    @Test
    void concurrencyBackendFailureUsesTheStableTranscriptionError() {
        ProviderConcurrencyLimiter limiter = mock(ProviderConcurrencyLimiter.class);
        when(limiter.acquire(RedisTranscriptionConcurrencyAdapter.PROVIDER_ID))
                .thenThrow(new IllegalStateException("Redis down"));
        RedisTranscriptionConcurrencyAdapter adapter =
                new RedisTranscriptionConcurrencyAdapter(limiter);

        assertThatThrownBy(adapter::acquire)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TRANSCRIPTION_UNAVAILABLE));
    }
}
