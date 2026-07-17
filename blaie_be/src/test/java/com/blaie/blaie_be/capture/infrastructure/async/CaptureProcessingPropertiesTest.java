package com.blaie.blaie_be.capture.infrastructure.async;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureProcessingPropertiesTest {

    @Test
    void defaultAllInOneRoleIsSafeAndUsesBoundedDispatchBackoff() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();

        assertThat(properties.isConfigurationValid()).isTrue();
        assertThat(properties.isRoleConfigurationValid()).isTrue();
        assertThat(properties.dispatchRetryDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.dispatchRetryDelay(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.dispatchRetryDelay(99)).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.streamRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.streamCleanupInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.schedulerPoolSize()).isEqualTo(1);
        assertThat(properties.maxActiveJobsPerUser()).isEqualTo(10);
        assertThat(properties.maxActiveJobsTotal()).isEqualTo(1_000);
        assertThat(properties.maxOldestQueuedAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.admissionRetryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void acceptanceAndRecoveryCannotRunWithoutDurablePublisher() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setPublisherEnabled(false);

        assertThat(properties.isRoleConfigurationValid()).isFalse();

        properties.setAcceptAsyncEnabled(false);
        assertThat(properties.isRoleConfigurationValid()).isFalse();

        properties.setRecoveryEnabled(false);
        assertThat(properties.isRoleConfigurationValid()).isTrue();
        assertThat(properties.workerEnabled()).isTrue();
    }

    @Test
    void queuedReconciliationMustWaitLongerThanOutboxRecovery() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setOutboxRecoveryAge(Duration.ofSeconds(10));
        properties.setDispatchRetryDelays(List.of(Duration.ofSeconds(10)));

        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setDispatchRetryDelays(List.of(Duration.ofSeconds(11)));
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void streamRetentionAndCleanupIntervalMustBePositive() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setStreamRetention(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setStreamRetention(Duration.ofHours(1));
        properties.setStreamCleanupInterval(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();
    }

    @Test
    void scheduledTriggerPoolMustBePositive() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setSchedulerPoolSize(0);

        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setSchedulerPoolSize(1);
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void admissionLimitsAndDurationsMustBePositive() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setMaxActiveJobsPerUser(0);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMaxActiveJobsPerUser(10);
        properties.setMaxActiveJobsTotal(0);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMaxActiveJobsTotal(9);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMaxActiveJobsTotal(10);
        properties.setMaxOldestQueuedAge(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMaxOldestQueuedAge(Duration.ofMinutes(1));
        properties.setAdmissionRetryAfter(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();
    }
}
