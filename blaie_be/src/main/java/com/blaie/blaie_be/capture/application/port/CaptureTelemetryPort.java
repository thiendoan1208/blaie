package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import java.time.Duration;

public interface CaptureTelemetryPort {
    void recordJobDuration(Duration duration, JobOutcome outcome);

    void recordProviderDuration(
            Duration duration,
            String providerId,
            ProviderOutcome outcome
    );

    void incrementProviderError(
            String providerId,
            CaptureFailureClass failureClass
    );

    void incrementRetry(RetrySource source);

    void incrementDead(
            DeadSource source,
            CaptureFailureClass failureClass
    );

    void incrementStaleRecovered(long count);

    void incrementQueuedRedispatched(long count);

    void incrementStorageError(StorageOperation operation);

    void incrementStorageOrphansFound(long count);

    void incrementStorageReferencesMissing(long count);

    void recordProviderConcurrencyWait(
            Duration duration,
            String providerId,
            ConcurrencyWaitOutcome outcome
    );

    enum JobOutcome {
        COMPLETED("completed"),
        RETRY_SCHEDULED("retry_scheduled"),
        DEAD("dead"),
        STALE_DISCARDED("stale_discarded");

        private final String value;

        JobOutcome(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    enum ProviderOutcome {
        SUCCESS("success"),
        FAILURE("failure");

        private final String value;

        ProviderOutcome(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    enum RetrySource {
        AUTOMATIC("automatic"),
        STALE_RECOVERY("stale_recovery"),
        MANUAL("manual"),
        ADMIN("admin");

        private final String value;

        RetrySource(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    enum DeadSource {
        WORKER("worker"),
        STALE_RECOVERY("stale_recovery"),
        OPERATOR("operator");

        private final String value;

        DeadSource(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    enum ConcurrencyWaitOutcome {
        ACQUIRED("acquired"),
        FAILED("failed"),
        INTERRUPTED("interrupted");

        private final String value;

        ConcurrencyWaitOutcome(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    enum StorageOperation {
        UPLOAD("upload"),
        DOWNLOAD("download"),
        SIGN_READ("sign_read"),
        DELETE("delete"),
        HEAD("head"),
        LIST("list");

        private final String value;

        StorageOperation(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
