package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import java.util.UUID;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishesCompleteWakeUpMessageWithoutProducerSideTrimming() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        TextCaptureQueuedEvent event = new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                4
        );
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));

        new RedisCaptureJobPublisher(redisTemplate, properties).publish(event);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<MapRecord<String, String, String>> recordCaptor =
                org.mockito.ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(recordCaptor.capture());
        MapRecord<String, String, String> record = recordCaptor.getValue();
        assertThat(record.getStream()).isEqualTo(properties.streamKey());
        assertThat(record.getValue()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "eventId", event.eventId().toString(),
                "jobId", event.jobId().toString(),
                "captureId", event.captureId().toString(),
                "dispatchGeneration", "4"
        ));
        verify(streams, never()).add(any(Record.class), any(XAddOptions.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void propagatesRedisFailureSoTheOutboxPublicationRemainsIncomplete() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        RedisConnectionFailureException failure = new RedisConnectionFailureException("redis unavailable");
        TextCaptureQueuedEvent event = new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1
        );
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(any(MapRecord.class))).thenThrow(failure);

        RedisCaptureJobPublisher publisher = new RedisCaptureJobPublisher(redisTemplate, properties);

        assertThatThrownBy(() -> publisher.publish(event)).isSameAs(failure);
    }
}
