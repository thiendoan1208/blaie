package com.blaie.blaie_be.capture.infrastructure.transcription;

import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.capture.application.port.SpeechToTextPort;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "blaie.transcription",
        name = "provider",
        havingValue = "groq",
        matchIfMissing = true
)
public class GroqSpeechToTextAdapter implements SpeechToTextPort {
    private final GroqTranscriptionProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GroqSpeechToTextAdapter(
            GroqTranscriptionProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    GroqSpeechToTextAdapter(
            GroqTranscriptionProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribe(AudioInput audio, String language) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AppException(ErrorCode.TRANSCRIPTION_NOT_CONFIGURED);
        }
        try {
            String response = restClient.post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(requestBody(audio, language))
                    .retrieve()
                    .body(String.class);
            return transcriptFrom(response);
        } catch (AppException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private MultiValueMap<String, Object> requestBody(AudioInput audio, String language) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedAudioResource(audio.bytes(), safeFilename(audio.filename())));
        body.add("model", properties.model());
        body.add("language", language);
        body.add("response_format", "json");
        body.add("temperature", "0");
        return body;
    }

    private String transcriptFrom(String response) {
        if (response == null || response.isBlank()) {
            throw unavailable(null);
        }
        JsonNode text = objectMapper.readTree(response).path("text");
        if (!text.isString()) {
            throw unavailable(null);
        }
        return text.asString();
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "recording.webm" : filename.trim();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank()) return "recording.webm";
        return value.substring(0, Math.min(value.length(), 100));
    }

    private AppException unavailable(Throwable cause) {
        AppException exception = new AppException(ErrorCode.TRANSCRIPTION_UNAVAILABLE);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private static final class NamedAudioResource extends ByteArrayResource {
        private final String filename;

        private NamedAudioResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
