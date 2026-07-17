package com.blaie.blaie_be.core.ratelimit.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigurationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void subjectHmacSecretMustContainAtLeastThirtyTwoCharacters() {
        RateLimitProperties properties = validProperties();
        properties.setSubjectHmacSecret("too-short");

        assertThat(validator.validate(properties))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("subjectHmacSecret"));

        properties.setSubjectHmacSecret("rate-limit-test-secret-at-least-32-bytes");
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void windowsMustBeAtLeastOneSecondAndUseUniqueKeyDurations() {
        RateLimitPolicy policy = new RateLimitPolicy(
                new RateLimitWindow(2, Duration.ofMillis(999)),
                new RateLimitWindow(3, Duration.ofSeconds(1))
        );

        assertThat(validator.validate(policy))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .contains("windows"));

        policy.setWindows(java.util.List.of(
                new RateLimitWindow(2, Duration.ofMillis(1_100)),
                new RateLimitWindow(3, Duration.ofMillis(1_900))
        ));
        assertThat(validator.validate(policy))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("windowDurationUnique"));

        policy.setWindows(java.util.List.of(
                new RateLimitWindow(2, Duration.ofSeconds(1)),
                new RateLimitWindow(3, Duration.ofSeconds(2))
        ));
        assertThat(validator.validate(policy)).isEmpty();
    }

    private RateLimitProperties validProperties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setSubjectHmacSecret("rate-limit-test-secret-at-least-32-bytes");
        return properties;
    }
}
