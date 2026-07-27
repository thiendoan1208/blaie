package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.application.AudioTranscriptionService;
import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudioTranscriptionControllerTest {
    private AudioTranscriptionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AudioTranscriptionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AudioTranscriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsOneMultipartAudioFileAndWrapsTheTranscript() throws Exception {
        when(service.transcribe(any(), eq("vi"))).thenReturn("Nhắc tôi gọi cho mẹ");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/transcriptions/audio")
                        .file(file)
                        .param("language", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Nhắc tôi gọi cho mẹ"));

        ArgumentCaptor<AudioInput> input = ArgumentCaptor.forClass(AudioInput.class);
        verify(service).transcribe(input.capture(), eq("vi"));
        assertThat(input.getValue().filename()).isEqualTo("voice.webm");
        assertThat(input.getValue().contentType()).isEqualTo("audio/webm");
        assertThat(input.getValue().bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void missingAudioPartReturnsTheStableAudioRequiredError() throws Exception {
        mockMvc.perform(multipart("/api/v1/transcriptions/audio"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUDIO_REQUIRED"));
    }

    @Test
    void applicationValidationErrorsKeepTheirStableCodeAndStatus() throws Exception {
        when(service.transcribe(any(), any())).thenThrow(
                new AppException(ErrorCode.AUDIO_TYPE_UNSUPPORTED)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1}
        );

        mockMvc.perform(multipart("/api/v1/transcriptions/audio").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AUDIO_TYPE_UNSUPPORTED"));
    }
}
