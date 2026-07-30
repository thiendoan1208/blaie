package com.blaie.blaie_be.capture.application.port;

import java.net.URI;
import java.time.Duration;

public interface ObjectStoragePort {
    void put(String objectKey, byte[] bytes, String contentType);

    byte[] get(String objectKey);

    URI createReadUri(String objectKey, Duration ttl);

    void delete(String objectKey);

    boolean exists(String objectKey);

    default StoredObjectPage list(String prefix, String continuationToken, int limit) {
        throw new UnsupportedOperationException("Object listing is not supported");
    }
}
