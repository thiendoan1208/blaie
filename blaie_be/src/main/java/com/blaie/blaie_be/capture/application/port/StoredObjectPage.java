package com.blaie.blaie_be.capture.application.port;

import java.util.List;

public record StoredObjectPage(
        List<StoredObject> objects,
        String nextContinuationToken
) {
    public StoredObjectPage {
        objects = List.copyOf(objects);
    }
}
