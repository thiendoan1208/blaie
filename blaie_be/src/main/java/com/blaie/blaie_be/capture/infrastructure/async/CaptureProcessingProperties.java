package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.capture.processing")
public class CaptureProcessingProperties implements CaptureProcessingSettingsPort {
    /** @deprecated use the role-specific flags instead. */
    @Deprecated
    private boolean enabled = true;
    private boolean acceptAsyncEnabled = true;
    private boolean publisherEnabled = true;
    private boolean workerEnabled = true;
    private boolean recoveryEnabled = true;
    private int maxAttempts = 4;
    private Duration idempotencyTtl = Duration.ofHours(24);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private List<Duration> retryDelays = new ArrayList<>(
            List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30))
    );
    private List<Duration> dispatchRetryDelays = new ArrayList<>(
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10))
    );
    private String streamKey = "blaie:capture:text-jobs";
    private String consumerGroup = "capture-text-workers";
    private String consumerName = "worker-" + UUID.randomUUID();
    private int batchSize = 10;
    private Duration readBlock = Duration.ofMillis(200);
    private Duration outboxRecoveryAge = Duration.ofSeconds(10);
    private Duration streamRetention = Duration.ofHours(24);
    private Duration streamCleanupInterval = Duration.ofHours(1);
    private int workerCorePoolSize = 1;
    private int workerMaxPoolSize = 1;
    private int workerQueueCapacity = 0;
    private Duration workerKeepAlive = Duration.ofSeconds(30);
    private Duration workerShutdownAwait = Duration.ofSeconds(30);
    private int heartbeatPoolSize = 1;
    private int eventCorePoolSize = 1;
    private int eventMaxPoolSize = 2;
    private int eventQueueCapacity = 100;

    @Override
    public boolean acceptAsyncEnabled() {
        return acceptAsyncEnabled;
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public Duration idempotencyTtl() {
        return idempotencyTtl;
    }

    @Override
    public Duration leaseDuration() {
        return leaseDuration;
    }

    @Override
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public Duration retryDelay(int failedAttemptCount) {
        if (retryDelays.isEmpty()) {
            return Duration.ZERO;
        }
        int index = Math.max(0, Math.min(failedAttemptCount - 1, retryDelays.size() - 1));
        return retryDelays.get(index);
    }

    @Override
    public Duration dispatchRetryDelay(int dispatchGeneration) {
        if (dispatchRetryDelays.isEmpty()) {
            return Duration.ZERO;
        }
        int index = Math.max(0, Math.min(dispatchGeneration - 1, dispatchRetryDelays.size() - 1));
        return dispatchRetryDelays.get(index);
    }

    public String streamKey() {
        return streamKey;
    }

    public String consumerGroup() {
        return consumerGroup;
    }

    public String consumerName() {
        return consumerName;
    }

    public int batchSize() {
        return batchSize;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean publisherEnabled() {
        return publisherEnabled;
    }

    public boolean workerEnabled() {
        return workerEnabled;
    }

    public boolean recoveryEnabled() {
        return recoveryEnabled;
    }

    public Duration readBlock() {
        return readBlock;
    }

    public Duration outboxRecoveryAge() {
        return outboxRecoveryAge;
    }

    public Duration streamRetention() {
        return streamRetention;
    }

    public Duration streamCleanupInterval() {
        return streamCleanupInterval;
    }

    public int workerCorePoolSize() {
        return workerCorePoolSize;
    }

    public int workerMaxPoolSize() {
        return workerMaxPoolSize;
    }

    public int workerQueueCapacity() {
        return workerQueueCapacity;
    }

    public Duration workerKeepAlive() {
        return workerKeepAlive;
    }

    public Duration workerShutdownAwait() {
        return workerShutdownAwait;
    }

    public int heartbeatPoolSize() {
        return heartbeatPoolSize;
    }

    public int eventCorePoolSize() {
        return eventCorePoolSize;
    }

    public int eventMaxPoolSize() {
        return eventMaxPoolSize;
    }

    public int eventQueueCapacity() {
        return eventQueueCapacity;
    }

    @AssertTrue(message = "capture processing configuration must use positive limits and durations")
    public boolean isConfigurationValid() {
        return maxAttempts > 0
                && idempotencyTtl != null && idempotencyTtl.isPositive()
                && leaseDuration != null && leaseDuration.isPositive()
                && heartbeatInterval != null && heartbeatInterval.isPositive()
                && heartbeatInterval.compareTo(leaseDuration) < 0
                && retryDelays != null && !retryDelays.isEmpty()
                && retryDelays.stream().allMatch(delay -> delay != null && !delay.isNegative())
                && dispatchRetryDelays != null && !dispatchRetryDelays.isEmpty()
                && dispatchRetryDelays.stream().allMatch(delay -> delay != null && delay.isPositive())
                && streamKey != null && !streamKey.isBlank()
                && consumerGroup != null && !consumerGroup.isBlank()
                && consumerName != null && !consumerName.isBlank()
                && batchSize > 0
                && readBlock != null && !readBlock.isNegative()
                && outboxRecoveryAge != null && !outboxRecoveryAge.isNegative()
                && streamRetention != null && streamRetention.isPositive()
                && streamCleanupInterval != null && streamCleanupInterval.isPositive()
                && dispatchRetryDelays.getFirst().compareTo(outboxRecoveryAge) > 0
                && workerCorePoolSize > 0
                && workerMaxPoolSize >= workerCorePoolSize
                && workerQueueCapacity >= 0
                && workerKeepAlive != null && workerKeepAlive.toSeconds() > 0
                && workerShutdownAwait != null && workerShutdownAwait.toSeconds() > 0
                && heartbeatPoolSize > 0
                && eventCorePoolSize > 0
                && eventMaxPoolSize >= eventCorePoolSize
                && eventQueueCapacity >= 0;
    }

    @AssertTrue(message = "capture acceptance and recovery require the durable publisher")
    public boolean isRoleConfigurationValid() {
        return (!acceptAsyncEnabled || publisherEnabled)
                && (!recoveryEnabled || publisherEnabled);
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAcceptAsyncEnabled(boolean acceptAsyncEnabled) {
        this.acceptAsyncEnabled = acceptAsyncEnabled;
    }

    public void setPublisherEnabled(boolean publisherEnabled) {
        this.publisherEnabled = publisherEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public void setRetryDelays(List<Duration> retryDelays) {
        this.retryDelays = new ArrayList<>(retryDelays);
    }

    public void setDispatchRetryDelays(List<Duration> dispatchRetryDelays) {
        this.dispatchRetryDelays = new ArrayList<>(dispatchRetryDelays);
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setReadBlock(Duration readBlock) {
        this.readBlock = readBlock;
    }

    public void setOutboxRecoveryAge(Duration outboxRecoveryAge) {
        this.outboxRecoveryAge = outboxRecoveryAge;
    }

    public void setStreamRetention(Duration streamRetention) {
        this.streamRetention = streamRetention;
    }

    public void setStreamCleanupInterval(Duration streamCleanupInterval) {
        this.streamCleanupInterval = streamCleanupInterval;
    }

    public void setWorkerCorePoolSize(int workerCorePoolSize) {
        this.workerCorePoolSize = workerCorePoolSize;
    }

    public void setWorkerMaxPoolSize(int workerMaxPoolSize) {
        this.workerMaxPoolSize = workerMaxPoolSize;
    }

    public void setWorkerQueueCapacity(int workerQueueCapacity) {
        this.workerQueueCapacity = workerQueueCapacity;
    }

    public void setWorkerKeepAlive(Duration workerKeepAlive) {
        this.workerKeepAlive = workerKeepAlive;
    }

    public void setWorkerShutdownAwait(Duration workerShutdownAwait) {
        this.workerShutdownAwait = workerShutdownAwait;
    }

    public void setHeartbeatPoolSize(int heartbeatPoolSize) {
        this.heartbeatPoolSize = heartbeatPoolSize;
    }

    public void setEventCorePoolSize(int eventCorePoolSize) {
        this.eventCorePoolSize = eventCorePoolSize;
    }

    public void setEventMaxPoolSize(int eventMaxPoolSize) {
        this.eventMaxPoolSize = eventMaxPoolSize;
    }

    public void setEventQueueCapacity(int eventQueueCapacity) {
        this.eventQueueCapacity = eventQueueCapacity;
    }
}
