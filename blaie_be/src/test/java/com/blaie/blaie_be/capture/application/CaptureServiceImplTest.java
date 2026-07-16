package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureServiceImplTest {
    @Test
    void captureSplitsOneInputIntoMultipleCompletedInboxItems() {
        UUID userId = UUID.randomUUID();
        InMemoryCaptureItemStore store = new InMemoryCaptureItemStore();
        TextClassifierPort classifier = text -> analysis(
                new ClassifiedTextItem("Meeting at 5 PM", CaptureCategory.CALENDAR_EVENT),
                new ClassifiedTextItem("Go running", CaptureCategory.TASK),
                new ClassifiedTextItem("Research hardcode engineering for me", CaptureCategory.INFORMATION)
        );
        CaptureService service = new CaptureServiceImpl(store, classifier);

        CaptureResult result = runAs(userId, () -> service.captureText("  Long mixed input  "));

        assertThat(result.originalText()).isEqualTo("Long mixed input");
        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(result.items()).extracting(CaptureItemResult::category).containsExactly(
                CaptureCategory.CALENDAR_EVENT, CaptureCategory.TASK, CaptureCategory.INFORMATION
        );
        assertThat(store.items).hasSize(3).allSatisfy(item -> assertThat(item.userId).isEqualTo(userId));
    }

    @Test
    void classifierFailureCreatesOneFailedFallbackInboxItemAndReturnsSafeError() {
        UUID userId = UUID.randomUUID();
        InMemoryCaptureItemStore store = new InMemoryCaptureItemStore();
        TextClassifierPort classifier = text -> {
            throw new TextClassificationException("ai_provider_unavailable", "provider details must not reach the client");
        };
        CaptureService service = new CaptureServiceImpl(store, classifier);

        assertThatThrownBy(() -> runAs(userId, () -> service.captureText("Buy milk")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);

        assertThat(store.items).singleElement().satisfies(item -> {
            assertThat(item.userId).isEqualTo(userId);
            assertThat(item.processingStatus).isEqualTo(ProcessingStatus.FAILED);
            assertThat(item.category).isNull();
            assertThat(item.originalText).isEqualTo("Buy milk");
        });
    }

    @Test
    void cancelledInputCompletesCaptureWithoutCreatingInboxItems() {
        UUID userId = UUID.randomUUID();
        InMemoryCaptureItemStore store = new InMemoryCaptureItemStore();
        CaptureService service = new CaptureServiceImpl(store, text -> analysis());

        CaptureResult result = runAs(userId, () -> service.captureText(
                "Hôm nay định đi chạy bộ mà mưa quá nên thôi bỏ qua đi"
        ));

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(result.items()).isEmpty();
        assertThat(store.items).isEmpty();
    }

    @Test
    void inboxItemIsNotVisibleToAnotherAuthenticatedUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        InMemoryCaptureItemStore store = new InMemoryCaptureItemStore();
        CaptureResult capture = store.createProcessing(ownerId, "Private note");
        CaptureItemResult item = store.markCompleted(capture.id(), analysis(
                new ClassifiedTextItem("Private note", CaptureCategory.INFORMATION)
        )).items().getFirst();
        CaptureService service = new CaptureServiceImpl(store, text -> analysis(
                new ClassifiedTextItem(text, CaptureCategory.INFORMATION)
        ));

        assertThatThrownBy(() -> runAs(otherUserId, () -> service.inboxItem(item.id())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_ITEM_NOT_FOUND);
    }

    private static CaptureAnalysis analysis(ClassifiedTextItem... items) {
        return new CaptureAnalysis(List.of(items), "deepseek", "test-model", "v4");
    }

    private <T> T runAs(UUID userId, java.util.function.Supplier<T> supplier) {
        return CurrentUserHolder.runAs(new CurrentUser(userId.toString(), false, Set.of()), supplier);
    }

    private static final class InMemoryCaptureItemStore implements CaptureItemStorePort {
        private final List<Capture> captures = new ArrayList<>();
        private final List<Item> items = new ArrayList<>();

        @Override
        public CaptureResult createProcessing(UUID userId, String originalText) {
            Capture capture = new Capture(UUID.randomUUID(), userId, originalText, ProcessingStatus.PROCESSING, Instant.now());
            captures.add(capture);
            return capture.toResult(List.of());
        }

        @Override
        public CaptureResult markCompleted(UUID captureId, CaptureAnalysis analysis) {
            Capture capture = capture(captureId);
            capture.processingStatus = ProcessingStatus.COMPLETED;
            List<CaptureItemResult> createdItems = analysis.items().stream()
                    .map(classified -> {
                        Item item = new Item(UUID.randomUUID(), capture.userId, capture.id, classified.originalText(),
                                classified.category(), ProcessingStatus.COMPLETED, Instant.now());
                        items.add(item);
                        return item.toResult();
                    })
                    .toList();
            return capture.toResult(createdItems);
        }

        @Override
        public void markFailed(UUID captureId, String failureCode) {
            Capture capture = capture(captureId);
            capture.processingStatus = ProcessingStatus.FAILED;
            items.add(new Item(UUID.randomUUID(), capture.userId, capture.id, capture.originalText,
                    null, ProcessingStatus.FAILED, Instant.now()));
        }

        @Override
        public Optional<CaptureItemResult> findOwned(UUID itemId, UUID userId) {
            return items.stream().filter(item -> item.id.equals(itemId) && item.userId.equals(userId))
                    .findFirst().map(Item::toResult);
        }

        @Override
        public List<CaptureItemResult> findFirstPage(UUID userId, int limit) {
            return items.stream().filter(item -> item.userId.equals(userId)).sorted(Item.ORDER).limit(limit)
                    .map(Item::toResult).toList();
        }

        @Override
        public List<CaptureItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit) {
            return items.stream().filter(item -> item.userId.equals(userId)).sorted(Item.ORDER)
                    .dropWhile(item -> item.createdAt.isAfter(createdAt)
                            || (item.createdAt.equals(createdAt) && item.id.compareTo(itemId) >= 0))
                    .limit(limit).map(Item::toResult).toList();
        }

        private Capture capture(UUID captureId) {
            return captures.stream().filter(capture -> capture.id.equals(captureId)).findFirst().orElseThrow();
        }

        private static final class Capture {
            private final UUID id;
            private final UUID userId;
            private final String originalText;
            private ProcessingStatus processingStatus;
            private final Instant createdAt;

            private Capture(UUID id, UUID userId, String originalText, ProcessingStatus processingStatus, Instant createdAt) {
                this.id = id;
                this.userId = userId;
                this.originalText = originalText;
                this.processingStatus = processingStatus;
                this.createdAt = createdAt;
            }

            private CaptureResult toResult(List<CaptureItemResult> items) {
                return new CaptureResult(id, originalText, processingStatus, items, createdAt);
            }
        }

        private static final class Item {
            private static final Comparator<Item> ORDER = Comparator
                    .comparing((Item item) -> item.createdAt).reversed()
                    .thenComparing(item -> item.id, Comparator.reverseOrder());
            private final UUID id;
            private final UUID userId;
            private final UUID captureId;
            private final String originalText;
            private final CaptureCategory category;
            private final ProcessingStatus processingStatus;
            private final Instant createdAt;

            private Item(UUID id, UUID userId, UUID captureId, String originalText, CaptureCategory category,
                         ProcessingStatus processingStatus, Instant createdAt) {
                this.id = id;
                this.userId = userId;
                this.captureId = captureId;
                this.originalText = originalText;
                this.category = category;
                this.processingStatus = processingStatus;
                this.createdAt = createdAt;
            }

            private CaptureItemResult toResult() {
                return new CaptureItemResult(id, originalText, category, processingStatus, createdAt);
            }
        }
    }
}
