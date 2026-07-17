package com.blaie.blaie_be.capture.infrastructure.async;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCaptureMessageFinalizerTest {

    @Test
    void acknowledgesAndDeletesExactRecordInOneRedisSevenCompatibleScript() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisCaptureMessageFinalizer finalizer = new RedisCaptureMessageFinalizer(redisTemplate, properties);
        RecordId recordId = RecordId.of("123-4");

        finalizer.acknowledgeAndDelete(recordId);

        verify(redisTemplate).execute(
                RedisCaptureMessageFinalizer.ACKNOWLEDGE_AND_DELETE,
                List.of(properties.streamKey()),
                properties.consumerGroup(),
                recordId.getValue()
        );
        String script = RedisCaptureMessageFinalizer.ACKNOWLEDGE_AND_DELETE.getScriptAsString();
        assertThat(script).contains("XACK", "XDEL").doesNotContain("XACKDEL");
        assertThat(script.indexOf("XACK")).isLessThan(script.indexOf("XDEL"));
    }

    @Test
    void redisFailureIsPropagatedSoWorkerCanLeaveDeliveryPending() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisCaptureMessageFinalizer finalizer = new RedisCaptureMessageFinalizer(redisTemplate, properties);
        RecordId recordId = RecordId.of("123-4");
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Redis unavailable");
        when(redisTemplate.execute(
                RedisCaptureMessageFinalizer.ACKNOWLEDGE_AND_DELETE,
                List.of(properties.streamKey()),
                properties.consumerGroup(),
                recordId.getValue()
        )).thenThrow(failure);

        assertThatThrownBy(() -> finalizer.acknowledgeAndDelete(recordId)).isSameAs(failure);
    }
}
