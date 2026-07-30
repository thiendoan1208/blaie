package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.CaptureAssetDraft;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.ImageCaptureSettingsPort;
import com.blaie.blaie_be.capture.application.port.ImageCaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.ImageInput;
import com.blaie.blaie_be.capture.application.port.ImageSanitizerPort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.SanitizedImage;
import com.blaie.blaie_be.capture.application.result.CaptureAssetResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImageCaptureServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T20:00:00Z");
    private static final byte[] RAW_IMAGE = new byte[]{1, 2, 3, 4};
    private static final byte[] SANITIZED_IMAGE = new byte[]{5, 6, 7, 8};

    private AuthorizationService authorization;
    private FakeWorkflowStore workflowStore;
    private FakeObjectStorage objectStorage;
    private ImageCaptureService service;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new CurrentUser(
                USER_ID.toString(),
                false,
                Set.of(PermissionAction.CAPTURE_CREATE.key())
        ));
        authorization = mock(AuthorizationService.class);
        workflowStore = new FakeWorkflowStore();
        objectStorage = new FakeObjectStorage();
        ImageSanitizerPort sanitizer = input -> new SanitizedImage(
                SANITIZED_IMAGE,
                "image/png",
                "png",
                20,
                10,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        service = new ImageCaptureService(
                workflowStore,
                objectStorage,
                sanitizer,
                enabledImageSettings(),
                processingSettings(),
                new CaptureContentPolicy(),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @AfterEach
    void clearUser() {
        CurrentUserHolder.clear();
    }

    @Test
    void imageOnlyCaptureUploadsSanitizedBytesAndStartsDurableWorkflow() {
        CaptureResult result = service.captureImage(
                null,
                new ImageInput("source.png", "image/png", RAW_IMAGE),
                IDEMPOTENCY_KEY.toString()
        );

        verify(authorization).require(PermissionAction.CAPTURE_CREATE);
        assertThat(objectStorage.putBytes).isEqualTo(SANITIZED_IMAGE);
        assertThat(objectStorage.putContentType).isEqualTo("image/png");
        assertThat(workflowStore.startedText).isNull();
        assertThat(workflowStore.startedAsset.objectKey()).isEqualTo(objectStorage.putObjectKey);
        assertThat(workflowStore.startedAsset.sha256())
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(result.inputType()).isEqualTo(CaptureInputType.IMAGE);
        assertThat(result.originalText()).isNull();
        assertThat(result.assets()).hasSize(1);
    }

    @Test
    void imageFingerprintMatchesTheFrontendVersionedByteLayout() {
        service.captureImage(
                "Read this",
                new ImageInput(
                        "source.png",
                        "image/png",
                        "private-image-bytes".getBytes(StandardCharsets.UTF_8)
                ),
                IDEMPOTENCY_KEY.toString()
        );

        assertThat(workflowStore.startedRequestHash)
                .isEqualTo("775f3edc220ba395e9f0b19483378a0b65edae9ecff23e24107cb5d123bb2c4e");
    }

    @Test
    void sameIdempotentReplayReturnsExistingCaptureWithoutUploadingAgain() {
        CaptureResult existing = imageCapture(UUID.randomUUID(), UUID.randomUUID(), "Existing note");
        workflowStore.existing = Optional.of(existing);

        CaptureResult result = service.captureImage(
                " Existing note ",
                new ImageInput("source.png", "image/png", RAW_IMAGE),
                IDEMPOTENCY_KEY.toString()
        );

        assertThat(result).isSameAs(existing);
        assertThat(objectStorage.putCalls).isZero();
        assertThat(workflowStore.startCalls).isZero();
    }

    @Test
    void databaseFailureAfterUploadTriggersBestEffortObjectDeletion() {
        workflowStore.startFailure = new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);

        assertThatThrownBy(() -> service.captureImage(
                "A note",
                new ImageInput("source.png", "image/png", RAW_IMAGE),
                IDEMPOTENCY_KEY.toString()
        )).isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).errorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(objectStorage.deleteObjectKey).isEqualTo(objectStorage.putObjectKey);
    }

    @Test
    void disabledImageAdmissionRejectsBeforeSanitizingOrUploading() {
        service = new ImageCaptureService(
                workflowStore,
                objectStorage,
                input -> {
                    throw new AssertionError("sanitizer must not run");
                },
                () -> false,
                processingSettings(),
                new CaptureContentPolicy(),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.captureImage(
                null,
                new ImageInput("source.png", "image/png", RAW_IMAGE),
                IDEMPOTENCY_KEY.toString()
        )).isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).errorCode())
                        .isEqualTo(ErrorCode.IMAGE_CAPTURE_DISABLED));
        assertThat(objectStorage.putCalls).isZero();
    }

    private ImageCaptureSettingsPort enabledImageSettings() {
        return () -> true;
    }

    private CaptureProcessingSettingsPort processingSettings() {
        return new CaptureProcessingSettingsPort() {
            @Override
            public boolean acceptAsyncEnabled() {
                return true;
            }

            @Override
            public int maxAttempts() {
                return 4;
            }

            @Override
            public Duration idempotencyTtl() {
                return Duration.ofHours(24);
            }

            @Override
            public Duration leaseDuration() {
                return Duration.ofSeconds(30);
            }

            @Override
            public Duration heartbeatInterval() {
                return Duration.ofSeconds(10);
            }

            @Override
            public Duration retryDelay(int failedAttemptCount) {
                return Duration.ofSeconds(2);
            }

            @Override
            public Duration dispatchRetryDelay(int dispatchGeneration) {
                return Duration.ofSeconds(30);
            }

            @Override
            public int maxActiveJobsPerUser() {
                return 10;
            }

            @Override
            public int maxActiveJobsTotal() {
                return 1_000;
            }

            @Override
            public Duration maxOldestQueuedAge() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration admissionRetryAfter() {
                return Duration.ofSeconds(30);
            }
        };
    }

    private CaptureResult imageCapture(UUID captureId, UUID assetId, String text) {
        Instant createdAt = NOW.minusSeconds(1);
        return new CaptureResult(
                captureId,
                CaptureInputType.IMAGE,
                text,
                ProcessingStatus.PROCESSING,
                null,
                false,
                List.of(new CaptureAssetResult(
                        assetId,
                        "image",
                        "image/png",
                        SANITIZED_IMAGE.length,
                        20,
                        10
                )),
                List.of(),
                createdAt,
                createdAt
        );
    }

    private static final class FakeWorkflowStore implements ImageCaptureWorkflowStorePort {
        private Optional<CaptureResult> existing = Optional.empty();
        private RuntimeException startFailure;
        private CaptureAssetDraft startedAsset;
        private String startedText;
        private String startedRequestHash;
        private int startCalls;

        @Override
        public Optional<CaptureResult> resolveOwnedSubmission(
                UUID userId,
                UUID idempotencyKey,
                String requestHash,
                Instant now
        ) {
            return existing;
        }

        @Override
        public CaptureResult startImageCapture(
                UUID userId,
                String originalText,
                CaptureAssetDraft asset,
                UUID idempotencyKey,
                String requestHash,
                String originRequestId,
                Instant now,
                Instant idempotencyExpiresAt,
                int maxAttempts
        ) {
            startCalls++;
            startedText = originalText;
            startedRequestHash = requestHash;
            startedAsset = asset;
            if (startFailure != null) throw startFailure;
            return imageCaptureResult(userId, asset, originalText, now);
        }

        private CaptureResult imageCaptureResult(
                UUID userId,
                CaptureAssetDraft asset,
                String originalText,
                Instant now
        ) {
            return new CaptureResult(
                    UUID.randomUUID(),
                    CaptureInputType.IMAGE,
                    originalText,
                    ProcessingStatus.PROCESSING,
                    null,
                    false,
                    List.of(new CaptureAssetResult(
                            asset.id(),
                            "image",
                            asset.contentType(),
                            asset.sizeBytes(),
                            asset.width(),
                            asset.height()
                    )),
                    List.of(),
                    now,
                    now
            );
        }
    }

    private static final class FakeObjectStorage implements ObjectStoragePort {
        private int putCalls;
        private String putObjectKey;
        private byte[] putBytes;
        private String putContentType;
        private String deleteObjectKey;

        @Override
        public void put(String objectKey, byte[] bytes, String contentType) {
            putCalls++;
            putObjectKey = objectKey;
            putBytes = bytes.clone();
            putContentType = contentType;
        }

        @Override
        public byte[] get(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.net.URI createReadUri(String objectKey, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            deleteObjectKey = objectKey;
        }

        @Override
        public boolean exists(String objectKey) {
            return false;
        }
    }
}
