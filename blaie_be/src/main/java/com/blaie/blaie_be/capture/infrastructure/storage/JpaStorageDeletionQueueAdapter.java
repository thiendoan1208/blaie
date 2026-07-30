package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.StorageDeletionQueuePort;
import com.blaie.blaie_be.capture.infrastructure.persistence.StorageDeletionJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaStorageDeletionQueueAdapter implements StorageDeletionQueuePort {
    private final StorageDeletionJobRepository repository;
    private final StorageDeletionProperties properties;

    public JpaStorageDeletionQueueAdapter(
            StorageDeletionJobRepository repository,
            StorageDeletionProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void enqueue(String objectKey, Instant now) {
        repository.insertIfAbsent(
                UUID.randomUUID(),
                objectKey,
                properties.maxAttempts(),
                now
        );
    }
}
