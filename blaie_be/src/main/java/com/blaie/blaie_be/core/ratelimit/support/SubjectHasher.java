package com.blaie.blaie_be.core.ratelimit.support;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SubjectHasher {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] secret;

    public SubjectHasher(RateLimitProperties properties) {
        this.secret = properties.subjectHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String purpose, String value) {
        String normalized = value == null || value.isBlank() ? "missing" : value.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(
                    mac.doFinal((purpose + ":" + normalized).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash rate limit subject", exception);
        }
    }
}
