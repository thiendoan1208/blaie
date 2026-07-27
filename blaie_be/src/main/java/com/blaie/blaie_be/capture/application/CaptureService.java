package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import java.util.List;
import java.util.UUID;

public interface CaptureService {
    CaptureResult captureText(String text, String idempotencyKey);

    CaptureResult capture(UUID captureId);

    CaptureResult resolveCapture(String idempotencyKey);

    List<CaptureResult> processingCaptures(int limit);

    CaptureResult retry(UUID captureId);

    void delete(UUID captureId);
}
