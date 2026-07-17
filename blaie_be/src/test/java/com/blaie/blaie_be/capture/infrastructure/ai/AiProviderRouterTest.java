package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderRouterTest {
    private static final ProviderConcurrencyLimiter NO_OP_LIMITER = providerId -> () -> { };

    @Test
    void retryablePrimaryFailureFallsBackToNextConfiguredProvider() {
        CaptureAnalysis expected = analysis("fallback");
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", failure(
                                "ai_provider_unavailable",
                                TextClassificationFailureClass.PROVIDER_RETRYABLE
                        )),
                        provider("fallback", text -> expected)
                ),
                "primary",
                "fallback"
        );

        assertThat(router.classify("Buy milk")).isSameAs(expected);
    }

    @Test
    void terminalFailureLocalToPrimaryFallsBackToNextConfiguredProvider() {
        CaptureAnalysis expected = analysis("fallback");
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", failure(
                                "ai_not_configured",
                                TextClassificationFailureClass.PROVIDER_TERMINAL
                        )),
                        provider("fallback", text -> expected)
                ),
                "primary",
                "fallback"
        );

        assertThat(router.classify("Buy milk")).isSameAs(expected);
    }

    @Test
    void contentTerminalFailureStopsTheWholeRoute() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        TextClassificationException failure = failure(
                "sensitive_credential_detected",
                TextClassificationFailureClass.CONTENT_TERMINAL
        );
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", failure),
                        provider("fallback", text -> {
                            fallbackCalls.incrementAndGet();
                            return analysis("fallback");
                        })
                ),
                "primary",
                "fallback"
        );

        assertThatThrownBy(() -> router.classify("secret"))
                .isSameAs(failure);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void systemFailureStopsProviderFallbackButRemainsAutomaticallyRetryable() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        TextClassificationException failure = failure(
                "unexpected_classification_error",
                TextClassificationFailureClass.SYSTEM_RETRYABLE
        );
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", failure),
                        provider("fallback", text -> {
                            fallbackCalls.incrementAndGet();
                            return analysis("fallback");
                        })
                ),
                "primary",
                "fallback"
        );

        assertThatThrownBy(() -> router.classify("Buy milk"))
                .isSameAs(failure);
        assertThat(failure.failureClass().automaticRetryAllowed()).isTrue();
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void anyRetryableProviderFailureWinsAfterTheWholeRouteFails() {
        TextClassificationException retryableFailure = failure(
                "ai_provider_unavailable",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", retryableFailure),
                        failingProvider("fallback", failure(
                                "ai_not_configured",
                                TextClassificationFailureClass.PROVIDER_TERMINAL
                        ))
                ),
                "primary",
                "fallback"
        );

        assertThatThrownBy(() -> router.classify("Buy milk"))
                .isSameAs(retryableFailure);
    }

    @Test
    void lastTerminalProviderFailureIsReturnedWhenNoProviderCanRecoverAutomatically() {
        TextClassificationException fallbackFailure = failure(
                "ai_provider_rejected",
                TextClassificationFailureClass.PROVIDER_TERMINAL
        );
        AiProviderRouter router = router(
                List.of(
                        failingProvider("primary", failure(
                                "ai_not_configured",
                                TextClassificationFailureClass.PROVIDER_TERMINAL
                        )),
                        failingProvider("fallback", fallbackFailure)
                ),
                "primary",
                "fallback"
        );

        assertThatThrownBy(() -> router.classify("Buy milk"))
                .isSameAs(fallbackFailure);
    }

    @Test
    void missingPrimaryAdapterDoesNotPreventAConfiguredFallback() {
        CaptureAnalysis expected = analysis("fallback");
        AiProviderRouter router = router(
                List.of(provider("fallback", text -> expected)),
                "missing",
                "fallback"
        );

        assertThat(router.classify("Buy milk")).isSameAs(expected);
    }

    @Test
    void missingRouteIsAProviderTerminalConfigurationFailure() {
        AiProviderRouter router = router(List.of(), "missing");

        assertThatThrownBy(() -> router.classify("Buy milk"))
                .isInstanceOf(TextClassificationException.class)
                .satisfies(exception -> {
                    TextClassificationException classificationException =
                            (TextClassificationException) exception;
                    assertThat(classificationException.failureCode())
                            .isEqualTo("ai_provider_not_configured");
                    assertThat(classificationException.failureClass())
                            .isEqualTo(TextClassificationFailureClass.PROVIDER_TERMINAL);
                });
    }

    @Test
    void providerCallIsWrappedByItsConcurrencyPermit() {
        List<String> lifecycle = new ArrayList<>();
        ProviderConcurrencyLimiter limiter = providerId -> {
            lifecycle.add("acquire:" + providerId);
            return () -> lifecycle.add("release:" + providerId);
        };
        AiProviderRouter router = router(
                List.of(provider("primary", text -> {
                    lifecycle.add("classify:primary");
                    return analysis("primary");
                })),
                limiter,
                "primary"
        );

        router.classify("Buy milk");

        assertThat(lifecycle).containsExactly(
                "acquire:primary",
                "classify:primary",
                "release:primary"
        );
    }

    @Test
    void providerPermitIsReleasedBeforeFallbackIsAcquired() {
        List<String> lifecycle = new ArrayList<>();
        ProviderConcurrencyLimiter limiter = providerId -> {
            lifecycle.add("acquire:" + providerId);
            return () -> lifecycle.add("release:" + providerId);
        };
        AiProviderRouter router = router(
                List.of(
                        provider("primary", text -> {
                            lifecycle.add("classify:primary");
                            throw failure(
                                    "ai_provider_unavailable",
                                    TextClassificationFailureClass.PROVIDER_RETRYABLE
                            );
                        }),
                        provider("fallback", text -> {
                            lifecycle.add("classify:fallback");
                            return analysis("fallback");
                        })
                ),
                limiter,
                "primary",
                "fallback"
        );

        router.classify("Buy milk");

        assertThat(lifecycle).containsExactly(
                "acquire:primary",
                "classify:primary",
                "release:primary",
                "acquire:fallback",
                "classify:fallback",
                "release:fallback"
        );
    }

    @Test
    void concurrencyBackendFailureStopsTheProviderRoute() {
        AtomicInteger providerCalls = new AtomicInteger();
        TextClassificationException backendFailure = failure(
                "ai_concurrency_backend_unavailable",
                TextClassificationFailureClass.SYSTEM_RETRYABLE
        );
        AiProviderRouter router = router(
                List.of(
                        provider("primary", text -> {
                            providerCalls.incrementAndGet();
                            return analysis("primary");
                        }),
                        provider("fallback", text -> {
                            providerCalls.incrementAndGet();
                            return analysis("fallback");
                        })
                ),
                providerId -> {
                    throw backendFailure;
                },
                "primary",
                "fallback"
        );

        assertThatThrownBy(() -> router.classify("Buy milk")).isSameAs(backendFailure);
        assertThat(providerCalls).hasValue(0);
    }

    private AiProviderRouter router(
            List<TextClassifierProvider> providers,
            String primary,
            String... fallbacks
    ) {
        return router(providers, NO_OP_LIMITER, primary, fallbacks);
    }

    private AiProviderRouter router(
            List<TextClassifierProvider> providers,
            ProviderConcurrencyLimiter limiter,
            String primary,
            String... fallbacks
    ) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProvider(primary);
        properties.setFallbackProviders(List.of(fallbacks));
        return new AiProviderRouter(providers, properties, limiter);
    }

    private CaptureAnalysis analysis(String provider) {
        return new CaptureAnalysis(List.of(), provider, "model", "v1");
    }

    private TextClassificationException failure(
            String failureCode,
            TextClassificationFailureClass failureClass
    ) {
        return new TextClassificationException(failureCode, "safe detail", failureClass);
    }

    private TextClassifierProvider failingProvider(
            String id,
            TextClassificationException failure
    ) {
        return provider(id, text -> {
            throw failure;
        });
    }

    private TextClassifierProvider provider(
            String id,
            Function<String, CaptureAnalysis> classifier
    ) {
        return new TextClassifierProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public CaptureAnalysis classify(String text) {
                return classifier.apply(text);
            }
        };
    }
}
