package com.blaie.blaie_be.capture.application.port;

import java.time.Instant;

public record StoredObject(String objectKey, Instant lastModified) {
}
