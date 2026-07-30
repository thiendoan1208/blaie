package com.blaie.blaie_be.capture.infrastructure.image;

import jakarta.validation.constraints.Min;
import com.blaie.blaie_be.capture.application.port.ImageCaptureSettingsPort;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.capture.image")
public class ImageCaptureProperties implements ImageCaptureSettingsPort {
    private boolean enabled;
    private boolean workerEnabled;

    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    @Min(1)
    private long maxPixels = 40_000_000L;

    @Min(1)
    private int maxWidth = 12_000;

    @Min(1)
    private int maxHeight = 12_000;

    @NotNull
    private Duration signedUrlTtl = Duration.ofMinutes(2);

    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean workerEnabled() {
        return workerEnabled;
    }

    public DataSize maxFileSize() {
        return maxFileSize;
    }

    public long maxPixels() {
        return maxPixels;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public Duration signedUrlTtl() {
        return signedUrlTtl;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setSignedUrlTtl(Duration signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }
}
