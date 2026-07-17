package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.InboxPageResult;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaptureServiceImpl implements CaptureService {
    private static final int MAX_LIMIT = 50;
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final CaptureItemStorePort captureItemStore;
    private final CaptureWorkflowStorePort workflowStore;
    private final CaptureProcessingSettingsPort settings;
    private final Clock clock;

    public CaptureServiceImpl(
            CaptureItemStorePort captureItemStore,
            CaptureWorkflowStorePort workflowStore,
            CaptureProcessingSettingsPort settings,
            Clock clock
    ) {
        this.captureItemStore = captureItemStore;
        this.workflowStore = workflowStore;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public CaptureResult captureText(String text, String idempotencyKey) {
        requireAsyncAcceptance();
        String originalText = requireText(text);
        UUID key = requireIdempotencyKey(idempotencyKey);
        Instant now = clock.instant();
        return workflowStore.startTextCapture(
                currentUserId(),
                originalText,
                key,
                requestHash(originalText),
                now,
                now.plus(settings.idempotencyTtl()),
                settings.maxAttempts()
        );
    }

    @Override
    public CaptureResult capture(UUID captureId) {
        return workflowStore.findOwned(captureId, currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_FOUND));
    }

    @Override
    public List<CaptureResult> processingCaptures(int limit) {
        return workflowStore.findOwnedProcessing(currentUserId(), validateLimit(limit));
    }

    @Override
    public CaptureResult retry(UUID captureId) {
        requireAsyncAcceptance();
        return workflowStore.retryOwned(captureId, currentUserId(), clock.instant());
    }

    private void requireAsyncAcceptance() {
        if (!settings.acceptAsyncEnabled()) {
            throw new AppException(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
        }
    }

    @Override
    public InboxPageResult inbox(String cursor, int limit) {
        int safeLimit = validateLimit(limit);
        UUID userId = currentUserId();
        Cursor decodedCursor = cursor == null || cursor.isBlank() ? null : decodeCursor(cursor);
        List<CaptureItemResult> records = decodedCursor == null
                ? captureItemStore.findFirstPage(userId, safeLimit + 1)
                : captureItemStore.findPageAfter(userId, decodedCursor.createdAt(), decodedCursor.itemId(), safeLimit + 1);

        boolean hasMore = records.size() > safeLimit;
        List<CaptureItemResult> items = hasMore ? records.subList(0, safeLimit) : records;
        String nextCursor = hasMore ? encodeCursor(items.getLast()) : null;
        return new InboxPageResult(List.copyOf(items), nextCursor, hasMore, safeLimit);
    }

    @Override
    public CaptureItemResult inboxItem(UUID itemId) {
        return captureItemStore.findOwned(itemId, currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_ITEM_NOT_FOUND));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(CurrentUserHolder.requireCurrentUser().userId());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "text is required");
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

    private String requestHash(String originalText) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(originalText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private String encodeCursor(CaptureItemResult item) {
        String value = item.createdAt() + "|" + item.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "cursor is invalid");
        }
    }

    private record Cursor(Instant createdAt, UUID itemId) {
    }
}
