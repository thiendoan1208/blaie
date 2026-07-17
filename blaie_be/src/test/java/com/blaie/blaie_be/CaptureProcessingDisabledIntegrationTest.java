package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.CaptureService;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureJobRecoveryScheduler;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import com.blaie.blaie_be.capture.infrastructure.async.RedisCaptureJobPublisher;
import com.blaie.blaie_be.capture.infrastructure.async.RedisCaptureJobWorker;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.enabled=false",
        "blaie.auth.access-token-secret=disabled-test-access-secret-at-least-32-bytes",
        "blaie.email.provider=log",
        "blaie.email.from=Blaie <no-reply@test.local>",
        "blaie.email.web-base-url=http://localhost:3000",
        "blaie.email.api-base-url=http://localhost:8080/api/v1",
        "blaie.email.verification-ttl=24h",
        "blaie.google.oauth.client-id=test-google-client-id",
        "blaie.google.oauth.client-secret=test-google-client-secret",
        "blaie.google.oauth.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
        "blaie.google.oauth.web-base-url=http://localhost:3000"
})
class CaptureProcessingDisabledIntegrationTest {

    @Autowired
    private CaptureProcessingProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CaptureService captureService;

    @Test
    void legacySwitchDisablesEveryAsyncRoleAndRejectsNewWorkBeforePersistence() {
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.acceptAsyncEnabled()).isFalse();
        assertThat(properties.publisherEnabled()).isFalse();
        assertThat(properties.workerEnabled()).isFalse();
        assertThat(properties.recoveryEnabled()).isFalse();

        assertThat(applicationContext.getBeansOfType(RedisCaptureJobPublisher.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(RedisCaptureJobWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CaptureJobRecoveryScheduler.class)).isEmpty();

        assertThatThrownBy(() -> captureService.captureText(
                "This request must never create a capture",
                UUID.randomUUID().toString()
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
    }
}
