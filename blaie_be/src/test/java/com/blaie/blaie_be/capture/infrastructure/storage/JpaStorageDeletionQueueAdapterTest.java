package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.infrastructure.persistence.StorageDeletionJobRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JpaStorageDeletionQueueAdapterTest {
    @Test
    void usesAnIdempotentDatabaseInsertForObjectDeletion() {
        StorageDeletionJobRepository repository = mock(StorageDeletionJobRepository.class);
        StorageDeletionProperties properties = new StorageDeletionProperties();
        properties.setMaxAttempts(8);
        Instant now = Instant.parse("2026-07-28T20:00:00Z");

        new JpaStorageDeletionQueueAdapter(repository, properties)
                .enqueue("captures/orphan.png", now);

        verify(repository).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("captures/orphan.png"),
                org.mockito.ArgumentMatchers.eq(8),
                org.mockito.ArgumentMatchers.eq(now)
        );
    }
}
