package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureJobDispatchPort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureAssetDraft;
import com.blaie.blaie_be.capture.application.port.ImageCaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.StorageDeletionQueuePort;
import com.blaie.blaie_be.capture.application.result.CaptureAssetResult;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.OwnedCaptureAssetResult;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaCaptureWorkflowAdapter implements CaptureWorkflowStorePort, ImageCaptureWorkflowStorePort {
    private static final String TEXT_CLASSIFICATION = "text_classification";
    private static final String IMAGE_ANALYSIS = "image_analysis";

    private final CaptureRepository captureRepository;
    private final CaptureAssetRepository captureAssetRepository;
    private final CaptureItemRepository captureItemRepository;
    private final ProcessingJobRepository jobRepository;
    private final CaptureIdempotencyKeyRepository idempotencyRepository;
    private final CaptureJobDispatchPort jobDispatch;
    private final CaptureProcessingSettingsPort settings;
    private final JpaCaptureAdmissionGuard admissionGuard;
    private final JpaCaptureJobRestartCoordinator restartCoordinator;
    private final StorageDeletionQueuePort deletionQueue;

    public JpaCaptureWorkflowAdapter(
            CaptureRepository captureRepository,
            CaptureAssetRepository captureAssetRepository,
            CaptureItemRepository captureItemRepository,
            ProcessingJobRepository jobRepository,
            CaptureIdempotencyKeyRepository idempotencyRepository,
            CaptureJobDispatchPort jobDispatch,
            CaptureProcessingSettingsPort settings,
            JpaCaptureAdmissionGuard admissionGuard,
            JpaCaptureJobRestartCoordinator restartCoordinator,
            StorageDeletionQueuePort deletionQueue
    ) {
        this.captureRepository = captureRepository;
        this.captureAssetRepository = captureAssetRepository;
        this.captureItemRepository = captureItemRepository;
        this.jobRepository = jobRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jobDispatch = jobDispatch;
        this.settings = settings;
        this.admissionGuard = admissionGuard;
        this.restartCoordinator = restartCoordinator;
        this.deletionQueue = deletionQueue;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaptureResult> resolveOwnedSubmission(
            UUID userId,
            UUID idempotencyKey,
            String requestHash,
            Instant now
    ) {
        return idempotencyRepository
                .findByUserIdAndIdempotencyKeyAndExpiresAtAfter(userId, idempotencyKey, now)
                .map(existing -> resolveExisting(existing, requestHash, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OwnedCaptureAssetResult> findOwnedAsset(
            UUID captureId,
            UUID assetId,
            UUID userId
    ) {
        if (captureRepository.findByIdAndUserId(captureId, userId).isEmpty()) {
            return Optional.empty();
        }
        return captureAssetRepository.findByIdAndCaptureId(assetId, captureId)
                .map(asset -> new OwnedCaptureAssetResult(
                        asset.id(),
                        asset.captureId(),
                        asset.objectKey()
                ));
    }

    @Override
    @Transactional
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

        CaptureEntity capture = captureRepository.saveAndFlush(
                CaptureEntity.processingImage(userId, originalText)
        );
        captureAssetRepository.saveAndFlush(CaptureAssetEntity.image(capture.id(), asset));
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

        ProcessingJobEntity job = jobRepository.save(ProcessingJobEntity.queued(
                capture,
                IMAGE_ANALYSIS,
                maxAttempts,
                originRequestId,
                now,
                now.plus(settings.dispatchRetryDelay(1))
        ));
        publishDispatch(job);
        return toCaptureResult(capture, job);
    }

    @Override
    @Transactional
    public CaptureResult startTextCapture(
            UUID userId,
            String originalText,
            UUID idempotencyKey,
            String requestHash,
            String originRequestId,
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
                        originRequestId,
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
                        jobRepository.findByCaptureId(capture.id()).orElse(null)
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaptureResult> findOwnedByIdempotencyKey(
            UUID idempotencyKey,
            UUID userId,
            Instant now
    ) {
        return idempotencyRepository
                .findByUserIdAndIdempotencyKeyAndExpiresAtAfter(userId, idempotencyKey, now)
                .flatMap(key -> findOwned(key.captureId(), userId));
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
                        jobRepository.findByCaptureId(capture.id()).orElse(null)
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
                .findLockedByCaptureId(captureId)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_RETRYABLE));
        CaptureEntity capture = captureRepository.findLockedByIdAndUserId(captureId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_FOUND));
        restartCoordinator.restart(job, capture, now, ErrorCode.CAPTURE_NOT_RETRYABLE);
        return toCaptureResult(capture, job);
    }

    @Override
    @Transactional
    public void deleteOwned(UUID captureId, UUID userId) {
        if (captureRepository.findByIdAndUserId(captureId, userId).isEmpty()) {
            throw new AppException(ErrorCode.CAPTURE_NOT_FOUND);
        }
        jobRepository.findLockedByCaptureId(captureId);
        CaptureEntity capture = captureRepository.findLockedByIdAndUserId(captureId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_FOUND));
        Instant now = Instant.now();
        captureAssetRepository.findByCaptureIdOrderByAssetPositionAsc(captureId)
                .forEach(asset -> deletionQueue.enqueue(asset.objectKey(), now));
        captureRepository.delete(capture);
        captureRepository.flush();
    }

    private void publishDispatch(ProcessingJobEntity job) {
        jobDispatch.publish(
                job.id(),
                job.captureId(),
                job.dispatchGeneration(),
                job.originRequestId()
        );
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
                .findByCaptureId(capture.id())
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
                CaptureInputType.fromValue(capture.inputType()),
                capture.originalText(),
                ProcessingStatus.fromValue(capture.processingStatus()),
                capture.failureCode(),
                canRetry,
                captureAssetRepository.findByCaptureIdOrderByAssetPositionAsc(capture.id())
                        .stream()
                        .map(this::toAssetResult)
                        .toList(),
                items,
                capture.createdAt(),
                capture.updatedAt()
        );
    }

    private CaptureAssetResult toAssetResult(CaptureAssetEntity asset) {
        return new CaptureAssetResult(
                asset.id(),
                asset.assetType(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.width(),
                asset.height()
        );
    }

    private CaptureItemResult toItemResult(CaptureItemEntity item) {
        return new CaptureItemResult(
                item.id(),
                item.captureId(),
                item.originalText(),
                item.category() == null ? null : CaptureCategory.fromValue(item.category()),
                ProcessingStatus.fromValue(item.processingStatus()),
                item.createdAt()
        );
    }
}
