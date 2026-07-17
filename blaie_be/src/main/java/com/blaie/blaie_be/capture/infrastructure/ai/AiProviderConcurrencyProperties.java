package com.blaie.blaie_be.capture.infrastructure.ai;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.ai.concurrency")
public class AiProviderConcurrencyProperties {
    private static final String PROVIDER_ID_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}";

    private boolean enabled = true;
    private String keyPrefix = "blaie:ai:concurrency";
    private int defaultLimit = 1;
    private Map<String, Integer> providerLimits = new LinkedHashMap<>();
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration renewalInterval = Duration.ofSeconds(10);
    private int renewalPoolSize = 1;
    private Duration pollInterval = Duration.ofMillis(100);

    public boolean enabled() {
        return enabled;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public Map<String, Integer> providerLimits() {
        return Map.copyOf(providerLimits);
    }

    public Duration leaseDuration() {
        return leaseDuration;
    }

    public Duration renewalInterval() {
        return renewalInterval;
    }

    public int renewalPoolSize() {
        return renewalPoolSize;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public int limitFor(String providerId) {
        String normalizedProviderId = normalizeProviderId(providerId);
        return providerLimits.entrySet().stream()
                .filter(entry -> normalizeProviderId(entry.getKey()).equals(normalizedProviderId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultLimit);
    }

    public String keyFor(String providerId) {
        return keyPrefix + ":" + normalizeProviderId(providerId);
    }

    @AssertTrue(message = "AI provider concurrency configuration must use positive limits and durations")
    public boolean isConfigurationValid() {
        if (keyPrefix == null || keyPrefix.isBlank()
                || defaultLimit <= 0
                || providerLimits == null
                || leaseDuration == null || leaseDuration.toMillis() <= 0
                || renewalInterval == null || renewalInterval.toMillis() <= 0
                || renewalInterval.multipliedBy(3).compareTo(leaseDuration) > 0
                || renewalPoolSize <= 0
                || pollInterval == null || pollInterval.toMillis() <= 0
                || pollInterval.compareTo(leaseDuration) >= 0) {
            return false;
        }
        if (providerLimits.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null
                        || !entry.getKey().matches(PROVIDER_ID_PATTERN)
                        || entry.getValue() == null
                        || entry.getValue() <= 0)) {
            return false;
        }
        Set<String> normalizedIds = providerLimits.keySet().stream()
                .map(this::normalizeProviderId)
                .collect(Collectors.toSet());
        return normalizedIds.size() == providerLimits.size();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public void setProviderLimits(Map<String, Integer> providerLimits) {
        this.providerLimits = providerLimits == null
                ? null
                : new LinkedHashMap<>(providerLimits);
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public void setRenewalInterval(Duration renewalInterval) {
        this.renewalInterval = renewalInterval;
    }

    public void setRenewalPoolSize(int renewalPoolSize) {
        this.renewalPoolSize = renewalPoolSize;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    private String normalizeProviderId(String providerId) {
        if (providerId == null || !providerId.matches(PROVIDER_ID_PATTERN)) {
            throw new IllegalArgumentException("Invalid AI provider id");
        }
        return providerId.toLowerCase(Locale.ROOT);
    }
}
