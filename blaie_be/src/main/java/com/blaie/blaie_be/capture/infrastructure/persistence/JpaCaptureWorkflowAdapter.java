package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaCaptureWorkflowAdapter implements CaptureWorkflowStorePort {
    private static final String TEXT_CLASSIFICATION = "text_classification";

    private final CaptureRepository captureRepository;
    private final CaptureItemRepository captureItemRepository;
    private final ProcessingJobRepository jobRepository;
    private final CaptureIdempotencyKeyRepository idempotencyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CaptureProcessingSettingsPort settings;
    private final JpaCaptureAdmissionGuard admissionGuard;

    public JpaCaptureWorkflowAdapter(
            CaptureRepository captureRepository,
            CaptureItemRepository captureItemRepository,
            ProcessingJobRepository jobRepository,
            CaptureIdempotencyKeyRepository idempotencyRepository,
            ApplicationEventPublisher eventPublisher,
            CaptureProcessingSettingsPort settings,
            JpaCaptureAdmissionGuard admissionGuard
    ) {
        this.captureRepository = captureRepository;
        this.captureItemRepository = captureItemRepository;
        this.jobRepository = jobRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.settings = settings;
        this.admissionGuard = admissionGuard;
    }

    @Override
    @Transactional
    public CaptureResult startTextCapture(
            UUID userId,
            String originalText,
            UUID idempotencyKey,
            String requestHash,
            Instant now,
            Instant idempotencyExpiresAt,
            int maxAttempts
    ) {
        idempotencyRepository.deleteExpired(userId, idempotencyKey, now);
        Optional<CaptureIdempotencyKeyEntity> existing =
                idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash, userId);
        }

        admissionGuard.acquireGlobalMutex();
        idempotencyRepository.deleteExpired(userId, idempotencyKey, now);
        existing = idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash, userId);
        }

        admissionGuard.requireCapacity(userId, now);

        CaptureEntity capture = captureRepository.saveAndFlush(CaptureEntity.processing(userId, originalText));
        int inserted = idempotencyRepository.insertIfAbsent(
                userId,
                idempotencyKey,
                requestHash,
                capture.id(),
                now,
                idempotencyExpiresAt
        );
        if (inserted == 0) {
            captureRepository.delete(capture);
            captureRepository.flush();
            CaptureIdempotencyKeyEntity concurrent = idempotencyRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key disappeared after conflict"));
            return resolveExisting(concurrent, requestHash, userId);
        }

        ProcessingJobEntity job = jobRepository.save(
                ProcessingJobEntity.queued(
                        capture,
                        maxAttempts,
                        now,
                        now.plus(settings.dispatchRetryDelay(1))
                )
        );
        publishDispatch(job);
        return toCaptureResult(capture, job);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaptureResult> findOwned(UUID captureId, UUID userId) {
        return captureRepository.findByIdAndUserId(captureId, userId)
                .map(capture -> toCaptureResult(
                        capture,
                        jobRepository.findByCaptureIdAndJobType(capture.id(), TEXT_CLASSIFICATION).orElse(null)
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaptureResult> findOwnedProcessing(UUID userId, int limit) {
        return captureRepository.findByUserIdAndProcessingStatusOrderByCreatedAtDescIdDesc(
                        userId,
                        ProcessingStatus.PROCESSING.value(),
                        PageRequest.of(0, limit)
                ).stream()
                .map(capture -> toCaptureResult(
                        capture,
                        jobRepository.findByCaptureIdAndJobType(capture.id(), TEXT_CLASSIFICATION).orElse(null)
                ))
                .toList();
    }

    @Override
    @Transactional
    public CaptureResult retryOwned(UUID captureId, UUID userId, Instant now) {
        if (captureRepository.findByIdAndUserId(captureId, userId).isEmpty()) {
            throw new AppException(ErrorCode.CAPTURE_NOT_FOUND);
        }
        ProcessingJobEntity job = jobRepository
                .findLockedByCaptureIdAndJobType(captureId, TEXT_CLASSIFICATION)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_RETRYABLE));
        CaptureEntity capture = captureRepository.findLockedByIdAndUserId(captureId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_FOUND));
        if (!ProcessingStatus.FAILED.value().equals(capture.processingStatus())
                || !ProcessingJobStatus.DEAD.value().equals(job.status())
                || !job.manualRetryAllowed()) {
            throw new AppException(ErrorCode.CAPTURE_NOT_RETRYABLE);
        }

        admissionGuard.acquireGlobalMutex();
        admissionGuard.requireCapacity(userId, now);

        captureItemRepository.deleteByCaptureId(captureId);
        capture.restart();
        job.restart(now, now.plus(settings.dispatchRetryDelay(job.dispatchGeneration() + 1)));
        captureRepository.flush();
        jobRepository.flush();
        publishDispatch(job);
        return toCaptureResult(capture, job);
    }

    private void publishDispatch(ProcessingJobEntity job) {
        eventPublisher.publishEvent(new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                job.id(),
                job.captureId(),
                job.dispatchGeneration()
        ));
    }

    private CaptureResult resolveExisting(
            CaptureIdempotencyKeyEntity existing,
            String requestHash,
            UUID userId
    ) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        CaptureEntity capture = captureRepository.findByIdAndUserId(existing.captureId(), userId)
                .orElseThrow(() -> new IllegalStateException("Capture for idempotency key is missing"));
        ProcessingJobEntity job = jobRepository
                .findByCaptureIdAndJobType(capture.id(), TEXT_CLASSIFICATION)
                .orElse(null);
        return toCaptureResult(capture, job);
    }

    private CaptureResult toCaptureResult(CaptureEntity capture, ProcessingJobEntity job) {
        List<CaptureItemResult> items = captureItemRepository.findByCaptureIdOrderByItemPositionAsc(capture.id())
                .stream()
                .map(this::toItemResult)
                .toList();
        boolean canRetry = ProcessingStatus.FAILED.value().equals(capture.processingStatus())
                && job != null
                && ProcessingJobStatus.DEAD.value().equals(job.status())
                && job.manualRetryAllowed();
        return new CaptureResult(
                capture.id(),
                capture.originalText(),
                ProcessingStatus.fromValue(capture.processingStatus()),
                capture.failureCode(),
                canRetry,
                items,
                capture.createdAt(),
                capture.updatedAt()
        );
    }

    private CaptureItemResult toItemResult(CaptureItemEntity item) {
        return new CaptureItemResult(
                item.id(),
                item.originalText(),
                item.category() == null ? null : CaptureCategory.fromValue(item.category()),
                ProcessingStatus.fromValue(item.processingStatus()),
                item.createdAt()
        );
    }
}
