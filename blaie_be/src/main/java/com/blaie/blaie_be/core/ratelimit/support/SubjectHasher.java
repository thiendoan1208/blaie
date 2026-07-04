package com.blaie.blaie_be.core.ratelimit.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SubjectHasher {
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String hash(String purpose, String value) {
        String normalized = value == null || value.isBlank() ? "missing" : value.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return BASE64_URL_ENCODER.encodeToString(
                    digest.digest((purpose + ":" + normalized).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash rate limit subject", exception);
        }
    }
}
