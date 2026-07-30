package com.blaie.blaie_be.capture.application.port;

import java.time.Instant;

public interface StorageDeletionQueuePort {
    void enqueue(String objectKey, Instant now);
}
