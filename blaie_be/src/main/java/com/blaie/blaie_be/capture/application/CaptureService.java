package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.InboxPageResult;
import java.util.UUID;

public interface CaptureService {
    CaptureResult captureText(String text);

    InboxPageResult inbox(String cursor, int limit);

    CaptureItemResult inboxItem(UUID itemId);
}
