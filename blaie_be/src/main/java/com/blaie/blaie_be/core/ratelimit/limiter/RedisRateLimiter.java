package com.blaie.blaie_be.core.ratelimit.limiter;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return { current, ttl }
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List<?>> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = createScript();
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // Redis exposes Lua array results through the raw List class token.
    private static DefaultRedisScript<List<?>> createScript() {
        return new DefaultRedisScript(SCRIPT, List.class);
    }

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        try {
            Duration retryAfter = Duration.ZERO;
            boolean denied = false;
            for (RateLimitWindow window : request.windows()) {
                List<?> result = redisTemplate.execute(
                        script,
                        List.of(key(request, window)),
                        String.valueOf(window.window().toMillis())
                );
                long current = longAt(result, 0);
                long ttlMillis = Math.max(0L, longAt(result, 1));
                if (current > window.permitLimit()) {
                    denied = true;
                    Duration windowRetryAfter = Duration.ofMillis(ttlMillis);
                    if (windowRetryAfter.compareTo(retryAfter) > 0) {
                        retryAfter = windowRetryAfter;
                    }
                }
            }
            return denied
                    ? RateLimitDecision.denied(request.policyName(), retryAfter)
                    : RateLimitDecision.allowed(request.policyName());
        } catch (RuntimeException exception) {
            boolean failOpen = request.failOpen() == null ? properties.failOpen() : request.failOpen();
            if (!failOpen) {
                throw new RateLimitBackendUnavailableException(exception);
            }
            log.warn("Rate limit check failed open for policy={}", request.policyName(), exception);
            return RateLimitDecision.allowed(request.policyName());
        }
    }

    private String key(RateLimitRequest request, RateLimitWindow window) {
        return "%s:%s:%s:%d".formatted(
                properties.keyPrefix(),
                request.policyName(),
                request.subject(),
                window.window().toSeconds()
        );
    }

    private long longAt(List<?> result, int index) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number number)) {
            throw new IllegalStateException("Redis rate limit script returned an invalid result");
        }
        return number.longValue();
    }
}
