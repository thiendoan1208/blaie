package com.blaie.blaie_be.core.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.security.cors")
public class SecurityCorsProperties {
    @NotEmpty
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @AssertTrue(message = "CORS allowed origins must be explicit and must not contain wildcards")
    public boolean isAllowedOriginsExplicit() {
        return allowedOrigins != null
                && !allowedOrigins.isEmpty()
                && allowedOrigins.stream().allMatch(origin -> origin != null && !origin.isBlank() && !origin.contains("*"));
    }
}
