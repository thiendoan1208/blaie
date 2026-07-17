package com.blaie.blaie_be.capture.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blaie.ai")
public class AiProviderProperties {
    private String provider = "deepseek";
    private List<String> fallbackProviders = new ArrayList<>();

    public String provider() {
        return provider;
    }

    public List<String> fallbackProviders() {
        return List.copyOf(fallbackProviders);
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setFallbackProviders(List<String> fallbackProviders) {
        this.fallbackProviders = new ArrayList<>(fallbackProviders);
    }
}
