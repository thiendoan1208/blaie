package com.blaie.blaie_be.capture.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blaie.ai.image")
public class ImageAiProperties {
    private String provider = "gemini";

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
