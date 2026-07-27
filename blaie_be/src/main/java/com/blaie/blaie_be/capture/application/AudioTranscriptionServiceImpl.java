package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.capture.application.port.SpeechToTextPort;
import com.blaie.blaie_be.capture.application.port.TranscriptionConcurrencyPort;
import com.blaie.blaie_be.capture.application.port.TranscriptionSettingsPort;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AudioTranscriptionServiceImpl implements AudioTranscriptionService {
    private static final String LANGUAGE_PATTERN = "[A-Za-z]{2,3}(?:-[A-Za-z]{2,8})?";

    private final SpeechToTextPort speechToText;
    private final TranscriptionConcurrencyPort concurrency;
    private final TranscriptionSettingsPort settings;
    private final AuthorizationService authorization;

    public AudioTranscriptionServiceImpl(
            SpeechToTextPort speechToText,
            TranscriptionConcurrencyPort concurrency,
            TranscriptionSettingsPort settings,
            AuthorizationService authorization
    ) {
        this.speechToText = speechToText;
        this.concurrency = concurrency;
        this.settings = settings;
        this.authorization = authorization;
    }

    @Override
    public String transcribe(AudioInput audio, String language) {
        authorization.require(PermissionAction.CAPTURE_CREATE);
        requireValidAudio(audio);
        String requestedLanguage = requireLanguage(language);

        String transcript;
        TranscriptionConcurrencyPort.Permit permit = concurrency.acquire();
        try (permit) {
            transcript = speechToText.transcribe(audio, requestedLanguage);
        }
        if (transcript == null || transcript.trim().isEmpty()) {
            throw new AppException(ErrorCode.TRANSCRIPTION_EMPTY);
        }
        String normalized = transcript.trim();
        if (normalized.length() > settings.maxTranscriptLength()) {
            throw new AppException(ErrorCode.TRANSCRIPTION_TOO_LONG);
        }
        return normalized;
    }

    private void requireValidAudio(AudioInput audio) {
        byte[] bytes = audio == null ? null : audio.bytes();
        if (bytes == null) {
            throw new AppException(ErrorCode.AUDIO_REQUIRED);
        }
        long size = bytes.length;
        if (size == 0) {
            throw new AppException(ErrorCode.AUDIO_EMPTY);
        }
        if (size > settings.maxFileSizeBytes()) {
            throw new AppException(ErrorCode.AUDIO_TOO_LARGE);
        }
        String contentType = normalizedContentType(audio.contentType());
        if (!settings.supportedContentTypes().contains(contentType)) {
            throw new AppException(ErrorCode.AUDIO_TYPE_UNSUPPORTED);
        }
    }

    private String requireLanguage(String language) {
        String value = language == null || language.isBlank()
                ? settings.defaultLanguage()
                : language.trim();
        if (value == null || !value.matches(LANGUAGE_PATTERN)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "language is invalid");
        }
        return value;
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) return "";
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
