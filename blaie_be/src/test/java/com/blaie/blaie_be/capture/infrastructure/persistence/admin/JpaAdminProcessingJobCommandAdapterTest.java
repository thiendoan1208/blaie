package com.blaie.blaie_be.capture.infrastructure.persistence.admin;

import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureItemRepository;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureRepository;
import com.blaie.blaie_be.capture.infrastructure.persistence.JpaCaptureJobRestartCoordinator;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaAdminProcessingJobCommandAdapterTest {
    @Test
    void markDeadPersistsReasonActorRequestAndStateTransitionInTheSameAdapterCall() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        UUID actorId = UUID.randomUUID();
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "text");
        ProcessingJobEntity job = ProcessingJobEntity.queued(
                capture,
                4,
                "origin-request",
                now.minusSeconds(10),
                now
        );
        ProcessingJobRepository jobRepository = mock(ProcessingJobRepository.class);
        CaptureRepository captureRepository = mock(CaptureRepository.class);
        CaptureItemRepository itemRepository = mock(CaptureItemRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jobRepository.findLockedById(job.id())).thenReturn(java.util.Optional.of(job));
        when(captureRepository.findLockedById(capture.id())).thenReturn(java.util.Optional.of(capture));
        JpaAdminProcessingJobCommandAdapter adapter = new JpaAdminProcessingJobCommandAdapter(
                jobRepository,
                captureRepository,
                itemRepository,
                mock(JpaCaptureJobRestartCoordinator.class),
                jdbcTemplate
        );

        var mutation = adapter.markDead(
                job.id(),
                "manual investigation confirmed poison input",
                actorId,
                "request-admin-operation",
                now
        );

        assertThat(mutation.previousStatus().value()).isEqualTo("queued");
        assertThat(mutation.job().status().value()).isEqualTo("dead");
        assertThat(capture.processingStatus()).isEqualTo("failed");
        verify(itemRepository).deleteByCaptureId(capture.id());
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue()[3]).isEqualTo(actorId.toString());
        assertThat(parameters.getValue()[4]).isEqualTo("request-admin-operation");
        assertThat(parameters.getValue()[5]).isEqualTo("mark_dead");
        assertThat(parameters.getValue()[6])
                .isEqualTo("manual investigation confirmed poison input");
        assertThat(parameters.getValue()[7]).isEqualTo("queued");
        assertThat(parameters.getValue()[8]).isEqualTo("dead");
    }
}
