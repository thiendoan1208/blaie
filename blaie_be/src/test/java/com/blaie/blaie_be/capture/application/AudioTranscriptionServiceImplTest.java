package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.application.DefaultAuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.capture.application.port.SpeechToTextPort;
import com.blaie.blaie_be.capture.application.port.TranscriptionConcurrencyPort;
import com.blaie.blaie_be.capture.application.port.TranscriptionSettingsPort;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioTranscriptionServiceImplTest {
    private SpeechToTextPort speechToText;
    private TranscriptionConcurrencyPort concurrency;
    private AuthorizationService authorization;
    private TranscriptionSettingsPort settings;
    private AudioTranscriptionService service;

    @BeforeEach
    void setUp() {
        speechToText = mock(SpeechToTextPort.class);
        concurrency = mock(TranscriptionConcurrencyPort.class);
        authorization = mock(AuthorizationService.class);
        settings = mock(TranscriptionSettingsPort.class);
        when(settings.defaultLanguage()).thenReturn("vi");
        when(settings.maxFileSizeBytes()).thenReturn(10L * 1024 * 1024);
        when(settings.maxTranscriptLength()).thenReturn(10_000);
        when(settings.supportedContentTypes()).thenReturn(Set.of(
                "audio/webm",
                "audio/mp4",
                "audio/ogg",
                "audio/wav",
                "audio/mpeg",
                "audio/mp3",
                "audio/m4a"
        ));
        when(concurrency.acquire()).thenReturn(() -> { });
        service = new AudioTranscriptionServiceImpl(
                speechToText,
                concurrency,
                settings,
                authorization
        );
    }

    @Test
    void transcribesValidatedAudioUsingDefaultLanguageAndCaptureCreatePermission() {
        AudioInput input = input("audio/webm;codecs=opus", "voice.webm", new byte[]{1, 2, 3});
        when(speechToText.transcribe(input, "vi")).thenReturn("  Nhắc tôi gọi cho mẹ  ");

        assertThat(service.transcribe(input, null)).isEqualTo("Nhắc tôi gọi cho mẹ");

        verify(authorization).require(PermissionAction.CAPTURE_CREATE);
        verify(concurrency).acquire();
        verify(speechToText).transcribe(input, "vi");
    }

    @Test
    void explicitLanguageIsNormalizedBeforeCallingProvider() {
        AudioInput input = input("audio/mp4", "voice.m4a", new byte[]{1});
        when(speechToText.transcribe(input, "en-US")).thenReturn("Call mom");

        assertThat(service.transcribe(input, " en-US ")).isEqualTo("Call mom");

        verify(speechToText).transcribe(input, "en-US");
    }

    @Test
    void emptyAudioIsRejectedBeforeConcurrencyOrProviderCalls() {
        AudioInput input = input("audio/webm", "voice.webm", new byte[0]);

        assertError(input, ErrorCode.AUDIO_EMPTY);

        verify(concurrency, never()).acquire();
        verify(speechToText, never()).transcribe(input, "vi");
    }

    @Test
    void oversizedAudioIsRejectedBeforeProviderCall() {
        AudioInput input = input("audio/webm", "voice.webm", new byte[10 * 1024 * 1024 + 1]);

        assertError(input, ErrorCode.AUDIO_TOO_LARGE);

        verify(speechToText, never()).transcribe(input, "vi");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/octet-stream", "video/webm", "text/plain"})
    void unsupportedAudioTypeIsRejected(String contentType) {
        AudioInput input = input(contentType, "voice.bin", new byte[]{1});

        assertError(input, ErrorCode.AUDIO_TYPE_UNSUPPORTED);

        verify(speechToText, never()).transcribe(input, "vi");
    }

    @Test
    void silentAudioReturnsAnExplicitEmptyTranscriptError() {
        AudioInput input = input("audio/ogg", "voice.ogg", new byte[]{1});
        when(speechToText.transcribe(input, "vi")).thenReturn("  ");

        assertError(input, ErrorCode.TRANSCRIPTION_EMPTY);
    }

    @Test
    void transcriptIsNeverSilentlyTruncated() {
        AudioInput input = input("audio/wav", "voice.wav", new byte[]{1});
        when(speechToText.transcribe(input, "vi")).thenReturn("a".repeat(10_001));

        assertError(input, ErrorCode.TRANSCRIPTION_TOO_LONG);
    }

    @Test
    void unauthenticatedAndUnauthorizedCallersNeverReachTheProvider() {
        AudioTranscriptionService protectedService = new AudioTranscriptionServiceImpl(
                speechToText,
                concurrency,
                settings,
                new DefaultAuthorizationService()
        );
        AudioInput input = input("audio/webm", "voice.webm", new byte[]{1});

        assertThatThrownBy(() -> protectedService.transcribe(input, null))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> CurrentUserHolder.runAs(
                new CurrentUser(UUID.randomUUID().toString(), false, Set.of()),
                () -> protectedService.transcribe(input, null)
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(speechToText, never()).transcribe(input, "vi");
    }

    private void assertError(AudioInput input, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.transcribe(input, null))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode));
    }

    private AudioInput input(String contentType, String filename, byte[] bytes) {
        return new AudioInput(filename, contentType, bytes);
    }
}
