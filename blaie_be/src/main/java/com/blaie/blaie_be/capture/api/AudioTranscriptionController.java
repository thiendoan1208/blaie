package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.api.response.AudioTranscriptionResponse;
import com.blaie.blaie_be.capture.application.AudioTranscriptionService;
import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.response.ApiResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/transcriptions")
public class AudioTranscriptionController {
    private final AudioTranscriptionService transcriptionService;

    public AudioTranscriptionController(AudioTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudioTranscriptionResponse> transcribe(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String language
    ) {
        try {
            AudioInput audio = new AudioInput(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            return ApiResponse.of(new AudioTranscriptionResponse(
                    transcriptionService.transcribe(audio, language)
            ));
        } catch (IOException exception) {
            throw new AppException(
                    ErrorCode.TRANSCRIPTION_UNAVAILABLE,
                    ErrorCode.TRANSCRIPTION_UNAVAILABLE.defaultMessage()
            );
        }
    }
}
