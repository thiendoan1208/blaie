package com.blaie.blaie_be.capture.infrastructure.transcription;

import com.blaie.blaie_be.capture.application.port.TranscriptionConcurrencyPort;
import com.blaie.blaie_be.capture.infrastructure.ai.ProviderConcurrencyLimiter;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RedisTranscriptionConcurrencyAdapter implements TranscriptionConcurrencyPort {
    static final String PROVIDER_ID = "groq-transcription";

    private final ProviderConcurrencyLimiter concurrencyLimiter;

    public RedisTranscriptionConcurrencyAdapter(ProviderConcurrencyLimiter concurrencyLimiter) {
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public Permit acquire() {
        try {
            ProviderConcurrencyLimiter.Permit permit =
                    concurrencyLimiter.acquire(PROVIDER_ID);
            return permit::close;
        } catch (RuntimeException exception) {
            AppException unavailable = new AppException(
                    ErrorCode.TRANSCRIPTION_UNAVAILABLE,
                    "Transcription capacity is temporarily unavailable"
            );
            unavailable.initCause(exception);
            throw unavailable;
        }
    }
}
