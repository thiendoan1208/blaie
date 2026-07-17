package com.blaie.blaie_be.capture.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextClassificationFailureClassTest {
    @Test
    void policiesKeepProviderFallbackAndJobRetriesIndependent() {
        assertPolicy(TextClassificationFailureClass.CONTENT_TERMINAL, false, false, false);
        assertPolicy(TextClassificationFailureClass.PROVIDER_TERMINAL, false, true, true);
        assertPolicy(TextClassificationFailureClass.PROVIDER_RETRYABLE, true, true, true);
        assertPolicy(TextClassificationFailureClass.SYSTEM_RETRYABLE, true, false, true);
    }

    @Test
    void persistedValuesRoundTripAndUnknownValuesFailClosed() {
        for (TextClassificationFailureClass failureClass : TextClassificationFailureClass.values()) {
            assertThat(TextClassificationFailureClass.fromValue(failureClass.value()))
                    .isSameAs(failureClass);
        }

        assertThatThrownBy(() -> TextClassificationFailureClass.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertPolicy(
            TextClassificationFailureClass failureClass,
            boolean automaticRetry,
            boolean providerFallback,
            boolean manualRetry
    ) {
        assertThat(failureClass.automaticRetryAllowed()).isEqualTo(automaticRetry);
        assertThat(failureClass.providerFallbackAllowed()).isEqualTo(providerFallback);
        assertThat(failureClass.manualRetryAllowed()).isEqualTo(manualRetry);
    }
}
