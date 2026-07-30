package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.CaptureJobQueuedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringCaptureJobDispatchAdapterTest {
    @Test
    void publishesTheSingleGenericCaptureJobEventContract() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SpringCaptureJobDispatchAdapter adapter = new SpringCaptureJobDispatchAdapter(publisher);
        UUID jobId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();

        adapter.publish(jobId, captureId, 4, "generic-dispatch-request");

        ArgumentCaptor<CaptureJobQueuedEvent> event =
                ArgumentCaptor.forClass(CaptureJobQueuedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().eventId()).isNotNull();
        assertThat(event.getValue().jobId()).isEqualTo(jobId);
        assertThat(event.getValue().captureId()).isEqualTo(captureId);
        assertThat(event.getValue().dispatchGeneration()).isEqualTo(4);
        assertThat(event.getValue().originRequestId()).isEqualTo("generic-dispatch-request");
    }
}
