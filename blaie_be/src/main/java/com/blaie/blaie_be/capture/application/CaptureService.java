package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.InboxPageResult;
import java.util.UUID;
import java.util.List;

public interface CaptureService {
    CaptureResult captureText(String text, String idempotencyKey);

    CaptureResult capture(UUID captureId);

    List<CaptureResult> processingCaptures(int limit);

    CaptureResult retry(UUID captureId);

    InboxPageResult inbox(String cursor, int limit);

    CaptureItemResult inboxItem(UUID itemId);
}
