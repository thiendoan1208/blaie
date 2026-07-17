package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class RedisProviderConcurrencyLimiter implements ProviderConcurrencyLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisProviderConcurrencyLimiter.class);
    private static final Permit NO_OP_PERMIT = () -> { };

    static final DefaultRedisScript<List<?>> ACQUIRE = acquireScript();
    static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            local redis_time = redis.call('TIME')
            local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
            local expires_at = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not expires_at then
              return 0
            end
            if tonumber(expires_at) <= now_ms then
              redis.call('ZREM', KEYS[1], ARGV[1])
              if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
              end
              return 0
            end
            local lease_ms = tonumber(ARGV[2])
            redis.call('ZADD', KEYS[1], 'XX', now_ms + lease_ms, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], lease_ms + 1000)
            return 1
            """, Long.class);
    static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            if redis.call('ZCARD', KEYS[1]) == 0 then
              redis.call('DEL', KEYS[1])
            end
            return removed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiProviderConcurrencyProperties properties;
    private final TaskScheduler renewalScheduler;

    public RedisProviderConcurrencyLimiter(
            StringRedisTemplate redisTemplate,
            AiProviderConcurrencyProperties properties,
            @Qualifier(AiProviderConcurrencyConfiguration.RENEWAL_SCHEDULER)
            TaskScheduler renewalScheduler
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.renewalScheduler = renewalScheduler;
    }

    @Override
    public Permit acquire(String providerId) {
        if (!properties.enabled()) {
            return NO_OP_PERMIT;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw interrupted(providerId, null);
        }

        String key = properties.keyFor(providerId);
        String ownerToken = UUID.randomUUID().toString();
        int limit = properties.limitFor(providerId);
        long startedAtNanos = System.nanoTime();
        while (true) {
            List<?> result;
            try {
                result = redisTemplate.execute(
                        ACQUIRE,
                        List.of(key),
                        ownerToken,
                        String.valueOf(limit),
                        String.valueOf(properties.leaseDuration().toMillis())
                );
            } catch (RuntimeException exception) {
                throw backendUnavailable(providerId, exception);
            }
            if (longAt(result, 0, providerId) == 1L) {
                long waitMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
                log.debug(
                        "AI provider concurrency permit acquired: provider={}, limit={}, waitMs={}",
                        providerId,
                        limit,
                        waitMillis
                );
                RedisPermit permit = new RedisPermit(providerId, key, ownerToken);
                try {
                    permit.startRenewal();
                    return permit;
                } catch (RuntimeException exception) {
                    permit.close();
                    throw renewalUnavailable(providerId, exception);
                }
            }
            try {
                Thread.sleep(properties.pollInterval().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw interrupted(providerId, exception);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List<?>> acquireScript() {
        String script = """
                local redis_time = redis.call('TIME')
                local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
                local lease_ms = tonumber(ARGV[3])
                redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now_ms)
                local current = redis.call('ZCARD', KEYS[1])
                if current < tonumber(ARGV[2]) then
                  local added = redis.call('ZADD', KEYS[1], 'NX', now_ms + lease_ms, ARGV[1])
                  if added == 1 then
                    redis.call('PEXPIRE', KEYS[1], lease_ms + 1000)
                    return { 1, current + 1 }
                  end
                end
                return { 0, current }
                """;
        return new DefaultRedisScript(script, List.class);
    }

    private long longAt(List<?> result, int index, String providerId) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number number)) {
            throw backendUnavailable(
                    providerId,
                    new IllegalStateException("Redis semaphore returned an invalid result")
            );
        }
        return number.longValue();
    }

    private TextClassificationException backendUnavailable(String providerId, RuntimeException cause) {
        return new TextClassificationException(
                "ai_concurrency_backend_unavailable",
                "AI provider concurrency backend is unavailable for " + providerId,
                TextClassificationFailureClass.SYSTEM_RETRYABLE,
                cause
        );
    }

    private TextClassificationException interrupted(String providerId, Throwable cause) {
        return new TextClassificationException(
                "ai_concurrency_wait_interrupted",
                "AI provider concurrency wait was interrupted for " + providerId,
                TextClassificationFailureClass.SYSTEM_RETRYABLE,
                cause
        );
    }

    private TextClassificationException renewalUnavailable(String providerId, RuntimeException cause) {
        return new TextClassificationException(
                "ai_concurrency_renewal_unavailable",
                "AI provider concurrency renewal is unavailable for " + providerId,
                TextClassificationFailureClass.SYSTEM_RETRYABLE,
                cause
        );
    }

    private final class RedisPermit implements Permit {
        private final String providerId;
        private final String key;
        private final String ownerToken;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean leaseLost = new AtomicBoolean();
        private volatile ScheduledFuture<?> renewalFuture;

        private RedisPermit(String providerId, String key, String ownerToken) {
            this.providerId = providerId;
            this.key = key;
            this.ownerToken = ownerToken;
        }

        private void startRenewal() {
            renewalFuture = renewalScheduler.scheduleAtFixedRate(
                    this::renew,
                    Instant.now().plus(properties.renewalInterval()),
                    properties.renewalInterval()
            );
            if (renewalFuture == null) {
                throw new IllegalStateException("AI provider concurrency renewal could not be scheduled");
            }
        }

        private void renew() {
            if (closed.get() || leaseLost.get()) {
                return;
            }
            try {
                Long renewed = redisTemplate.execute(
                        RENEW,
                        List.of(key),
                        ownerToken,
                        String.valueOf(properties.leaseDuration().toMillis())
                );
                if (renewed == null || renewed != 1L) {
                    markLeaseLost();
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "AI provider concurrency permit renewal failed; lease may expire: provider={}",
                        providerId,
                        exception
                );
            }
        }

        private void markLeaseLost() {
            if (!leaseLost.compareAndSet(false, true)) {
                return;
            }
            log.warn("AI provider concurrency permit lease is no longer owned: provider={}", providerId);
            ScheduledFuture<?> future = renewalFuture;
            if (future != null) {
                future.cancel(false);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> future = renewalFuture;
            if (future != null) {
                future.cancel(false);
            }
            try {
                redisTemplate.execute(RELEASE, List.of(key), ownerToken);
                log.debug("AI provider concurrency permit released: provider={}", providerId);
            } catch (RuntimeException exception) {
                log.warn(
                        "AI provider concurrency permit release failed; lease will expire: provider={}",
                        providerId,
                        exception
                );
            }
        }
    }
}
