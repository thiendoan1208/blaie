package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import java.util.UUID;
import org.slf4j.MDC;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCaptureJobPublisherTest {

    @Test
    void publishesCompleteWakeUpMessageWithoutProducerSideTrimming() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock();
        TextCaptureQueuedEvent event = new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                "publisher-request-123"
        );
        doReturn(streams).when(redisTemplate).opsForStream();
        MDC.put("existing", "preserved");
        when(streams.add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>any()))
                .thenAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isEqualTo(event.originRequestId());
            assertThat(MDC.get("eventId")).isEqualTo(event.eventId().toString());
            assertThat(MDC.get("jobId")).isEqualTo(event.jobId().toString());
            assertThat(MDC.get("captureId")).isEqualTo(event.captureId().toString());
            return RecordId.of("1-0");
        });

        try {
            new RedisCaptureJobPublisher(redisTemplate, properties).publish(event);
        } finally {
            assertThat(MDC.get("existing")).isEqualTo("preserved");
            assertThat(MDC.get("requestId")).isNull();
            MDC.clear();
        }

        org.mockito.ArgumentCaptor<MapRecord<String, String, String>> recordCaptor =
                org.mockito.ArgumentCaptor.captor();
        verify(streams).add(recordCaptor.capture());
        MapRecord<String, String, String> record = recordCaptor.getValue();
        assertThat(record.getStream()).isEqualTo(properties.streamKey());
        assertThat(record.getValue()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "eventId", event.eventId().toString(),
                "jobId", event.jobId().toString(),
                "captureId", event.captureId().toString(),
                "dispatchGeneration", "4",
                "originRequestId", event.originRequestId()
        ));
        verify(streams, never()).add(
                org.mockito.ArgumentMatchers.<Record<String, ?>>any(),
                any(XAddOptions.class)
        );
    }

    @Test
    void propagatesRedisFailureSoTheOutboxPublicationRemainsIncomplete() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock();
        RedisConnectionFailureException failure = new RedisConnectionFailureException("redis unavailable");
        TextCaptureQueuedEvent event = new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "publisher-failure-request"
        );
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>any()))
                .thenThrow(failure);

        RedisCaptureJobPublisher publisher = new RedisCaptureJobPublisher(redisTemplate, properties);

        MDC.put("existing", "preserved");
        try {
            assertThatThrownBy(() -> publisher.publish(event)).isSameAs(failure);
            assertThat(MDC.get("existing")).isEqualTo("preserved");
            assertThat(MDC.get("requestId")).isNull();
        } finally {
            MDC.clear();
        }
    }

    @Test
    void missingLegacyOriginRequestIdFallsBackToEventId() {
        UUID eventId = UUID.randomUUID();

        TextCaptureQueuedEvent event = new TextCaptureQueuedEvent(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                null
        );

        assertThat(event.originRequestId()).isEqualTo(eventId.toString());
    }
}
