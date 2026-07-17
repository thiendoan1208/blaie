package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureContentPolicyTest {
    private final CaptureContentPolicy policy = new CaptureContentPolicy();

    @Test
    void ordinaryInboxTextIsAllowed() {
        assertThatCode(() -> policy.validate("Nhắc tôi mua sữa lúc 5 giờ"))
                .doesNotThrowAnyException();
    }

    @Test
    void credentialLikeTextIsBlockedWithoutRetry() {
        assertThatThrownBy(() -> policy.validate("save sk-abcdefghijklmnopqrstuvwxyz123456"))
                .isInstanceOf(TextClassificationException.class)
                .satisfies(exception -> {
                    TextClassificationException classificationException =
                            (TextClassificationException) exception;
                    assertThat(classificationException.failureCode())
                            .isEqualTo("sensitive_credential_detected");
                    assertThat(classificationException.retryable()).isFalse();
                });
    }
}
