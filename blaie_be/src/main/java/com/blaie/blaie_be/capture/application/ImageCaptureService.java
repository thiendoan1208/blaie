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
import com.blaie.blaie_be.capture.application.port.StorageDeletionQueuePort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysisException;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ImageCaptureService {
    private static final Logger log = LoggerFactory.getLogger(ImageCaptureService.class);
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final ImageCaptureWorkflowStorePort workflowStore;
    private final ObjectStoragePort objectStorage;
    private final ImageSanitizerPort sanitizer;
    private final ImageCaptureSettingsPort imageSettings;
    private final CaptureProcessingSettingsPort processingSettings;
    private final CaptureContentPolicy contentPolicy;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final StorageDeletionQueuePort deletionQueue;

    @Autowired
    public ImageCaptureService(
            ImageCaptureWorkflowStorePort workflowStore,
            ObjectStoragePort objectStorage,
            ImageSanitizerPort sanitizer,
            ImageCaptureSettingsPort imageSettings,
            CaptureProcessingSettingsPort processingSettings,
            CaptureContentPolicy contentPolicy,
            AuthorizationService authorization,
            Clock clock,
            StorageDeletionQueuePort deletionQueue
    ) {
        this.workflowStore = workflowStore;
        this.objectStorage = objectStorage;
        this.sanitizer = sanitizer;
        this.imageSettings = imageSettings;
        this.processingSettings = processingSettings;
        this.contentPolicy = contentPolicy;
        this.authorization = authorization;
        this.clock = clock;
        this.deletionQueue = deletionQueue;
    }

    ImageCaptureService(
            ImageCaptureWorkflowStorePort workflowStore,
            ObjectStoragePort objectStorage,
            ImageSanitizerPort sanitizer,
            ImageCaptureSettingsPort imageSettings,
            CaptureProcessingSettingsPort processingSettings,
            CaptureContentPolicy contentPolicy,
            AuthorizationService authorization,
            Clock clock
    ) {
        this(
                workflowStore,
                objectStorage,
                sanitizer,
                imageSettings,
                processingSettings,
                contentPolicy,
                authorization,
                clock,
                (objectKey, now) -> {
                }
        );
    }

    public CaptureResult captureImage(String text, ImageInput image, String idempotencyKey) {
        authorization.require(PermissionAction.CAPTURE_CREATE);
        if (!imageSettings.enabled()) {
            throw new AppException(ErrorCode.IMAGE_CAPTURE_DISABLED);
        }
        if (!processingSettings.acceptAsyncEnabled()) {
            throw new AppException(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
        }

        String normalizedText = normalizeOptionalText(text);
        if (normalizedText != null) {
            requireSafeContent(normalizedText);
        }
        UUID key = requireIdempotencyKey(idempotencyKey);
        UUID userId = currentUserId();
        Instant now = clock.instant();
        String requestHash = requestHash(normalizedText, image.bytes());

        Optional<CaptureResult> existing = workflowStore.resolveOwnedSubmission(
                userId,
                key,
                requestHash,
                now
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        SanitizedImage sanitized;
        try {
            sanitized = sanitizer.sanitize(image);
        } catch (CaptureAnalysisException exception) {
            throw imageValidationException(exception);
        }

        UUID assetId = UUID.randomUUID();
        String objectKey = "captures/" + assetId + "." + sanitized.extension();
        CaptureAssetDraft asset = new CaptureAssetDraft(
                assetId,
                0,
                "r2",
                objectKey,
                sanitized.contentType(),
                sanitized.bytes().length,
                sanitized.sha256(),
                sanitized.width(),
                sanitized.height()
        );

        boolean uploaded = false;
        try {
            objectStorage.put(objectKey, sanitized.bytes(), sanitized.contentType());
            uploaded = true;
            CaptureResult result = workflowStore.startImageCapture(
                    userId,
                    normalizedText,
                    asset,
                    key,
                    requestHash,
                    RequestContextHolder.currentRequestId()
                            .orElseGet(() -> UUID.randomUUID().toString()),
                    now,
                    now.plus(processingSettings.idempotencyTtl()),
                    processingSettings.maxAttempts()
            );
            boolean acceptedThisAsset = result.assets().stream()
                    .anyMatch(candidate -> candidate.id().equals(assetId));
            if (!acceptedThisAsset) {
                safeDelete(assetId, objectKey);
            }
            return result;
        } catch (RuntimeException exception) {
            if (uploaded) {
                safeDelete(assetId, objectKey);
            }
            throw exception;
        }
    }

    private void safeDelete(UUID assetId, String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.error("Image upload compensation failed: assetId={}", assetId, cleanupFailure);
            try {
                deletionQueue.enqueue(objectKey, clock.instant());
            } catch (RuntimeException queueFailure) {
                log.error("Image orphan deletion could not be queued: assetId={}", assetId, queueFailure);
            }
        }
    }

    private String normalizeOptionalText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "text must not exceed " + MAX_TEXT_LENGTH + " characters"
            );
        }
        return normalized;
    }

    private void requireSafeContent(String text) {
        try {
            contentPolicy.validate(text);
        } catch (CaptureAnalysisException exception) {
            throw new AppException(ErrorCode.CAPTURE_SENSITIVE_CONTENT);
        }
    }

    private UUID requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(CurrentUserHolder.requireCurrentUser().userId());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String requestHash(String normalizedText, byte[] rawImage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] text = normalizedText == null
                    ? new byte[0]
                    : normalizedText.getBytes(StandardCharsets.UTF_8);
            digest.update("image-v1".getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(text.length).array());
            digest.update(text);
            digest.update(rawImage);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AppException imageValidationException(CaptureAnalysisException exception) {
        ErrorCode errorCode = switch (exception.failureCode()) {
            case "image_empty" -> ErrorCode.IMAGE_EMPTY;
            case "image_too_large" -> ErrorCode.IMAGE_TOO_LARGE;
            case "image_type_unsupported" -> ErrorCode.IMAGE_TYPE_UNSUPPORTED;
            case "image_dimensions_unsupported" -> ErrorCode.IMAGE_DIMENSIONS_UNSUPPORTED;
            default -> ErrorCode.IMAGE_INVALID;
        };
        return new AppException(errorCode);
    }
}
