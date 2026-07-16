package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.InboxPageResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaptureServiceImpl implements CaptureService {
    private static final int MAX_LIMIT = 50;

    private final CaptureItemStorePort captureItemStore;
    private final TextClassifierPort textClassifier;

    public CaptureServiceImpl(CaptureItemStorePort captureItemStore, TextClassifierPort textClassifier) {
        this.captureItemStore = captureItemStore;
        this.textClassifier = textClassifier;
    }

    @Override
    public CaptureResult captureText(String text) {
        String originalText = requireText(text);
        UUID userId = currentUserId();
        CaptureResult processingCapture = captureItemStore.createProcessing(userId, originalText);

        try {
            CaptureAnalysis analysis = textClassifier.classify(originalText);
            return captureItemStore.markCompleted(processingCapture.id(), analysis);
        } catch (TextClassificationException exception) {
            captureItemStore.markFailed(processingCapture.id(), exception.failureCode());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException exception) {
            captureItemStore.markFailed(processingCapture.id(), "unexpected_classifier_error");
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
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
        return text.trim();
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
