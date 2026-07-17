package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaCaptureItemStoreAdapter implements CaptureItemStorePort {
    private final CaptureItemRepository captureItemRepository;

    public JpaCaptureItemStoreAdapter(CaptureItemRepository captureItemRepository) {
        this.captureItemRepository = captureItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaptureItemResult> findOwned(UUID itemId, UUID userId) {
        return captureItemRepository.findByIdAndUserId(itemId, userId).map(this::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaptureItemResult> findFirstPage(UUID userId, int limit) {
        return captureItemRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit)).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaptureItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit) {
        return captureItemRepository.findPageAfter(userId, createdAt, itemId, PageRequest.of(0, limit)).stream()
                .map(this::toResult)
                .toList();
    }

    private CaptureItemResult toResult(CaptureItemEntity item) {
        return new CaptureItemResult(
                item.id(),
                item.originalText(),
                item.category() == null ? null : CaptureCategory.fromValue(item.category()),
                ProcessingStatus.fromValue(item.processingStatus()),
                item.createdAt()
        );
    }
}
