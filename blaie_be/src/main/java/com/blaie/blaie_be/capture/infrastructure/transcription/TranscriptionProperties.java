package com.blaie.blaie_be.capture.infrastructure.transcription;

import com.blaie.blaie_be.capture.application.port.TranscriptionSettingsPort;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.transcription")
public class TranscriptionProperties implements TranscriptionSettingsPort {
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/webm",
            "audio/mp4",
            "audio/ogg",
            "audio/wav",
            "audio/mpeg",
            "audio/mp3",
            "audio/m4a"
    );

    private String provider = "groq";
    private String defaultLanguage = "en";
    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private Duration maxDuration = Duration.ofSeconds(60);
    private int maxTranscriptLength = 10_000;

    public String provider() {
        return provider;
    }

    @Override
    public String defaultLanguage() {
        return defaultLanguage;
    }

    @Override
    public long maxFileSizeBytes() {
        return maxFileSize.toBytes();
    }

    public Duration maxDuration() {
        return maxDuration;
    }

    @Override
    public int maxTranscriptLength() {
        return maxTranscriptLength;
    }

    @Override
    public Set<String> supportedContentTypes() {
        return SUPPORTED_CONTENT_TYPES;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void setMaxDuration(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    public void setMaxTranscriptLength(int maxTranscriptLength) {
        this.maxTranscriptLength = maxTranscriptLength;
    }
}
