package com.blaie.blaie_be.core.ratelimit.limiter;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRateLimiterIntegrationTest {
    private static final int REDIS_PORT = 6379;

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void disconnectFromRedis() {
        connectionFactory.destroy();
    }

    @Test
    void allowsTheNthRequestAndDeniesTheNextWithRetryAfter() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setKeyPrefix("test:rate-limit:" + UUID.randomUUID());
        properties.setSubjectHmacSecret("rate-limit-test-secret-at-least-32-bytes");
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, properties);
        Duration window = Duration.ofSeconds(30);
        RateLimitRequest request = new RateLimitRequest(
                "capture-text",
                "user.hash:ip.hash",
                List.of(new RateLimitWindow(2, window)),
                false
        );

        RateLimitDecision first = limiter.check(request);
        RateLimitDecision second = limiter.check(request);
        RateLimitDecision third = limiter.check(request);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfter()).isPositive().isLessThanOrEqualTo(window);
    }
}
