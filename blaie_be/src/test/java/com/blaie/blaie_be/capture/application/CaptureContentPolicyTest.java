package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureContentPolicyTest {
    private final CaptureContentPolicy policy = new CaptureContentPolicy();

    @ParameterizedTest
    @MethodSource("blockedContent")
    void blocksSensitiveContentBeforeItCanReachPersistence(String text, String failureCode) {
        assertThatThrownBy(() -> policy.validate(text))
                .isInstanceOfSatisfying(TextClassificationException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(failureCode));
    }

    private static Stream<Arguments> blockedContent() {
        return Stream.of(
                Arguments.of("ASIAABCDEFGHIJKLMNOP", "sensitive_credential_detected"),
                Arguments.of("glpat-abcdefghijklmnopqrstuvwxyz", "sensitive_credential_detected"),
                Arguments.of("xoxb-1234567890-secret", "sensitive_credential_detected"),
                Arguments.of("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature", "sensitive_credential_detected"),
                Arguments.of("postgres://admin:password@example.com/app", "sensitive_credential_detected"),
                Arguments.of("__BLAIE_PII_EMAIL_fake__", "reserved_privacy_token_detected"),
                Arguments.of("123-45-6789", "sensitive_personal_identifier_detected"),
                Arguments.of("4111 1111 1111 1111", "sensitive_personal_identifier_detected")
        );
    }
}
