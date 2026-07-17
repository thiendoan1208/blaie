package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("6a99337a-65aa-4f19-a2c4-8796434a1ee8");

    @Test
    void captureTextCreatesProcessingWorkflowWithoutCallingAiInTheRequest() {
        UUID userId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(new InMemoryCaptureItemStore(), workflowStore);

        CaptureResult result = runAs(userId, () -> service.captureText(
                "  Meeting at 5 PM and go running  ",
                IDEMPOTENCY_KEY.toString()
        ));

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(result.items()).isEmpty();
        assertThat(workflowStore.userId).isEqualTo(userId);
        assertThat(workflowStore.originalText).isEqualTo("Meeting at 5 PM and go running");
        assertThat(workflowStore.idempotencyKey).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(workflowStore.requestHash).hasSize(64);
        assertThat(workflowStore.now).isEqualTo(NOW);
        assertThat(workflowStore.expiresAt).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void captureTextRequiresUuidIdempotencyKey() {
        CaptureService service = service(new InMemoryCaptureItemStore(), new InMemoryWorkflowStore());

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.captureText("Buy milk", null)))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.captureText("Buy milk", "not-a-uuid")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    @Test
    void captureLookupAlwaysUsesAuthenticatedUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(new InMemoryCaptureItemStore(), workflowStore);
        CaptureResult created = runAs(ownerId, () -> service.captureText("Private", IDEMPOTENCY_KEY.toString()));

        assertThatThrownBy(() -> runAs(otherId, () -> service.capture(created.id())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_NOT_FOUND);
    }

    @Test
    void inboxItemIsNotVisibleToAnotherAuthenticatedUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        InMemoryCaptureItemStore itemStore = new InMemoryCaptureItemStore();
        CaptureItemResult item = itemStore.add(ownerId, "Private note");
        CaptureService service = service(itemStore, new InMemoryWorkflowStore());

        assertThatThrownBy(() -> runAs(otherUserId, () -> service.inboxItem(item.id())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_ITEM_NOT_FOUND);
    }

    private CaptureService service(CaptureItemStorePort itemStore, CaptureWorkflowStorePort workflowStore) {
        CaptureProcessingSettingsPort settings = new CaptureProcessingSettingsPort() {
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
            public Duration retryDelay(int failedAttemptCount) {
                return Duration.ofSeconds(2);
            }
        };
        return new CaptureServiceImpl(
                itemStore,
                workflowStore,
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private <T> T runAs(UUID userId, java.util.function.Supplier<T> supplier) {
        return CurrentUserHolder.runAs(new CurrentUser(userId.toString(), false, Set.of()), supplier);
    }

    private static final class InMemoryWorkflowStore implements CaptureWorkflowStorePort {
        private final List<OwnedCapture> captures = new ArrayList<>();
        private UUID userId;
        private String originalText;
        private UUID idempotencyKey;
        private String requestHash;
        private Instant now;
        private Instant expiresAt;

        @Override
        public CaptureResult startTextCapture(
                UUID userId,
                String originalText,
                UUID idempotencyKey,
                String requestHash,
                Instant now,
                Instant idempotencyExpiresAt,
                int maxAttempts
        ) {
            this.userId = userId;
            this.originalText = originalText;
            this.idempotencyKey = idempotencyKey;
            this.requestHash = requestHash;
            this.now = now;
            this.expiresAt = idempotencyExpiresAt;
            CaptureResult result = new CaptureResult(
                    UUID.randomUUID(),
                    originalText,
                    ProcessingStatus.PROCESSING,
                    null,
                    false,
                    List.of(),
                    now,
                    now
            );
            captures.add(new OwnedCapture(userId, result));
            return result;
        }

        @Override
        public Optional<CaptureResult> findOwned(UUID captureId, UUID userId) {
            return captures.stream()
                    .filter(capture -> capture.userId().equals(userId) && capture.result().id().equals(captureId))
                    .map(OwnedCapture::result)
                    .findFirst();
        }

        @Override
        public List<CaptureResult> findOwnedProcessing(UUID userId, int limit) {
            return captures.stream()
                    .filter(capture -> capture.userId().equals(userId))
                    .limit(limit)
                    .map(OwnedCapture::result)
                    .toList();
        }

        @Override
        public CaptureResult retryOwned(UUID captureId, UUID userId, Instant now) {
            return findOwned(captureId, userId).orElseThrow();
        }
    }

    private record OwnedCapture(UUID userId, CaptureResult result) {
    }

    private static final class InMemoryCaptureItemStore implements CaptureItemStorePort {
        private final List<OwnedItem> items = new ArrayList<>();

        private CaptureItemResult add(UUID userId, String text) {
            CaptureItemResult result = new CaptureItemResult(
                    UUID.randomUUID(),
                    text,
                    CaptureCategory.INFORMATION,
                    ProcessingStatus.COMPLETED,
                    NOW
            );
            items.add(new OwnedItem(userId, result));
            return result;
        }

        @Override
        public Optional<CaptureItemResult> findOwned(UUID itemId, UUID userId) {
            return items.stream()
                    .filter(item -> item.userId().equals(userId) && item.result().id().equals(itemId))
                    .map(OwnedItem::result)
                    .findFirst();
        }

        @Override
        public List<CaptureItemResult> findFirstPage(UUID userId, int limit) {
            return items.stream().filter(item -> item.userId().equals(userId)).limit(limit)
                    .map(OwnedItem::result).toList();
        }

        @Override
        public List<CaptureItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit) {
            return findFirstPage(userId, limit);
        }
    }

    private record OwnedItem(UUID userId, CaptureItemResult result) {
    }
}
