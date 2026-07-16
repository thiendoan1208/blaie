package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureItemStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
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
    private final CaptureRepository captureRepository;

    public JpaCaptureItemStoreAdapter(CaptureItemRepository captureItemRepository, CaptureRepository captureRepository) {
        this.captureItemRepository = captureItemRepository;
        this.captureRepository = captureRepository;
    }

    @Override
    @Transactional
    public CaptureResult createProcessing(UUID userId, String originalText) {
        CaptureEntity capture = captureRepository.save(CaptureEntity.processing(userId, originalText));
        return toCaptureResult(capture, List.of());
    }

    @Override
    @Transactional
    public CaptureResult markCompleted(UUID captureId, CaptureAnalysis analysis) {
        CaptureEntity capture = captureRepository.findById(captureId)
                .orElseThrow(() -> new IllegalStateException("Capture disappeared during classification"));
        capture.complete(analysis);
        List<CaptureItemResult> items = captureItemRepository.saveAll(
                        analysis.items().stream()
                                .map(classifiedItem -> CaptureItemEntity.completed(capture, classifiedItem))
                                .toList()
                ).stream()
                .map(this::toResult)
                .toList();
        return toCaptureResult(capture, items);
    }

    @Override
    @Transactional
    public void markFailed(UUID captureId, String failureCode) {
        CaptureEntity capture = captureRepository.findById(captureId)
                .orElseThrow(() -> new IllegalStateException("Capture disappeared during classification"));
        capture.fail(failureCode);
        captureItemRepository.save(CaptureItemEntity.failed(capture));
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

    private CaptureResult toCaptureResult(CaptureEntity capture, List<CaptureItemResult> items) {
        return new CaptureResult(
                capture.id(),
                capture.originalText(),
                ProcessingStatus.fromValue(capture.processingStatus()),
                items,
                capture.createdAt()
        );
    }
}
