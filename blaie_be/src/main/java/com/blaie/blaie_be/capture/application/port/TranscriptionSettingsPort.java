package com.blaie.blaie_be.capture.application.port;

import java.util.Set;

public interface TranscriptionSettingsPort {
    String defaultLanguage();

    long maxFileSizeBytes();

    int maxTranscriptLength();

    Set<String> supportedContentTypes();
}
