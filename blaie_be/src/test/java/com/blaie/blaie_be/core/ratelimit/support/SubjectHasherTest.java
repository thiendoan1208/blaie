package com.blaie.blaie_be.core.ratelimit.support;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectHasherTest {
    private static final String SECRET = "rate-limit-test-secret-at-least-32-bytes";

    @Test
    void hashesNormalizedSubjectWithPurposeSeparatedHmacSha256() throws Exception {
        SubjectHasher hasher = hasher(SECRET);

        String actual = hasher.hash("user", "  User@Example.COM  ");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal("user:user@example.com".getBytes(StandardCharsets.UTF_8))
        );
        assertThat(actual).isEqualTo(expected);
        assertThat(actual).doesNotContain("User", "example.com");
    }

    @Test
    void purposeAndSecretProduceIndependentHashes() {
        SubjectHasher first = hasher(SECRET);
        SubjectHasher second = hasher("another-rate-limit-secret-at-least-32-bytes");

        assertThat(first.hash("user", "same-value"))
                .isNotEqualTo(first.hash("ip", "same-value"))
                .isNotEqualTo(second.hash("user", "same-value"));
        assertThat(first.hash("user", null))
                .isEqualTo(first.hash("user", " missing "));
    }

    private SubjectHasher hasher(String secret) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setSubjectHmacSecret(secret);
        return new SubjectHasher(properties);
    }
}
