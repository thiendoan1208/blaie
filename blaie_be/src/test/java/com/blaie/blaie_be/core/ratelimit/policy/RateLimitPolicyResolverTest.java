package com.blaie.blaie_be.core.ratelimit.policy;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.support.ClientIpResolver;
import com.blaie.blaie_be.core.ratelimit.support.SubjectHasher;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyResolverTest {
    private static final String SECRET = "rate-limit-test-secret-at-least-32-bytes";

    @Test
    void captureUsesAuthenticatedUserAndRemoteAddressInFailClosedCompositeSubject() {
        RateLimitProperties properties = properties();
        SubjectHasher hasher = new SubjectHasher(properties);
        RateLimitPolicyResolver resolver = resolver(properties, hasher);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/captures/text");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        RateLimitRequest resolved = CurrentUserHolder.runAs(
                new CurrentUser(userId.toString(), false, Set.of()),
                () -> resolver.resolve(request).orElseThrow()
        );

        assertThat(resolved.policyName()).isEqualTo("capture-text");
        assertThat(resolved.failOpen()).isFalse();
        assertThat(resolved.subject()).isEqualTo(
                "user." + hasher.hash("user", userId.toString())
                        + ":ip." + hasher.hash("ip", "203.0.113.10")
        );
        assertThat(resolved.subject()).doesNotContain(userId.toString(), "203.0.113.10", "198.51.100.99");
        assertThat(resolved.windows()).hasSize(2);
    }

    @Test
    void onlyExactCaptureWriteRoutesReceiveCapturePolicies() {
        RateLimitProperties properties = properties();
        RateLimitPolicyResolver resolver = resolver(properties, new SubjectHasher(properties));
        MockHttpServletRequest retry = new MockHttpServletRequest(
                "POST",
                "/api/v1/captures/2fca95eb-3d84-4f50-99bc-3e778936a6bb/retry"
        );
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/v1/captures");

        assertThat(resolver.resolve(retry)).get()
                .extracting(RateLimitRequest::policyName)
                .isEqualTo("capture-retry");
        assertThat(resolver.resolve(read)).isEmpty();
    }

    private RateLimitPolicyResolver resolver(RateLimitProperties properties, SubjectHasher hasher) {
        return new RateLimitPolicyResolver(
                properties,
                new ClientIpResolver(properties),
                hasher,
                new ObjectMapper()
        );
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setSubjectHmacSecret(SECRET);
        return properties;
    }
}
