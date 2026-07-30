package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.infrastructure.persistence.StorageDeletionJobEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.StorageDeletionJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StorageDeletionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StorageDeletionScheduler.class);

    private final StorageDeletionJobRepository repository;
    private final ObjectStoragePort storage;
    private final StorageDeletionProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String workerId = "storage-delete-" + UUID.randomUUID();

    public StorageDeletionScheduler(
            StorageDeletionJobRepository repository,
            ObjectStoragePort storage,
            StorageDeletionProperties properties,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${blaie.storage.deletion.interval:10s}")
    public void drain() {
        if (!properties.enabled()) {
            return;
        }
        recoverStale();
        for (int index = 0; index < properties.batchSize(); index++) {
            ClaimedDeletion claimed = claimOne();
            if (claimed == null) {
                return;
            }
            delete(claimed);
        }
    }

    private ClaimedDeletion claimOne() {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            List<StorageDeletionJobEntity> ready =
                    repository.findReady(now, PageRequest.of(0, 1));
            if (ready.isEmpty()) {
                return null;
            }
            StorageDeletionJobEntity job = ready.getFirst();
            if (!job.claim(workerId, now, now.plus(properties.leaseDuration()))) {
                return null;
            }
            return new ClaimedDeletion(job.id(), job.objectKey(), job.attemptCount());
        });
    }

    private void delete(ClaimedDeletion claimed) {
        try {
            storage.delete(claimed.objectKey());
            transactions.executeWithoutResult(status -> repository.findById(claimed.id())
                    .ifPresent(job -> job.complete(workerId, claimed.attempt(), clock.instant())));
        } catch (RuntimeException exception) {
            log.warn(
                    "Storage deletion retry scheduled: deletionJobId={}, attempt={}",
                    claimed.id(),
                    claimed.attempt()
            );
            transactions.executeWithoutResult(status -> repository.findById(claimed.id())
                    .ifPresent(job -> job.retry(
                            workerId,
                            claimed.attempt(),
                            "storage_delete_failed",
                            clock.instant().plus(retryDelay(claimed.attempt())),
                            clock.instant()
                    )));
        }
    }

    private void recoverStale() {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            repository.findStale(now, PageRequest.of(0, properties.batchSize()))
                    .forEach(job -> job.recover(now));
        });
    }

    private Duration retryDelay(int attempt) {
        long seconds = Math.min(3600, 5L << Math.min(10, Math.max(0, attempt - 1)));
        return Duration.ofSeconds(seconds);
    }

    private record ClaimedDeletion(UUID id, String objectKey, int attempt) {
    }
}
