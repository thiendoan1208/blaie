package com.blaie.blaie_be.capture.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureFailureClassTest {
    @Test
    void policiesKeepProviderFallbackAndJobRetriesIndependent() {
        assertPolicy(CaptureFailureClass.CONTENT_TERMINAL, false, false, false);
        assertPolicy(CaptureFailureClass.PROVIDER_TERMINAL, false, true, true);
        assertPolicy(CaptureFailureClass.PROVIDER_RETRYABLE, true, true, true);
        assertPolicy(CaptureFailureClass.SYSTEM_RETRYABLE, true, false, true);
    }

    @Test
    void persistedValuesRoundTripAndUnknownValuesFailClosed() {
        for (CaptureFailureClass failureClass : CaptureFailureClass.values()) {
            assertThat(CaptureFailureClass.fromValue(failureClass.value()))
                    .isSameAs(failureClass);
        }

        assertThatThrownBy(() -> CaptureFailureClass.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertPolicy(
            CaptureFailureClass failureClass,
            boolean automaticRetry,
            boolean providerFallback,
            boolean manualRetry
    ) {
        assertThat(failureClass.automaticRetryAllowed()).isEqualTo(automaticRetry);
        assertThat(failureClass.providerFallbackAllowed()).isEqualTo(providerFallback);
        assertThat(failureClass.manualRetryAllowed()).isEqualTo(manualRetry);
    }
}
