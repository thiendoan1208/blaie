package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.CaptureJobQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureJobDispatchPort;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringCaptureJobDispatchAdapter implements CaptureJobDispatchPort {
    private final ApplicationEventPublisher eventPublisher;

    public SpringCaptureJobDispatchAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(
            UUID jobId,
            UUID captureId,
            int dispatchGeneration,
            String originRequestId
    ) {
        eventPublisher.publishEvent(new CaptureJobQueuedEvent(
                UUID.randomUUID(),
                jobId,
                captureId,
                dispatchGeneration,
                originRequestId
        ));
    }
}
