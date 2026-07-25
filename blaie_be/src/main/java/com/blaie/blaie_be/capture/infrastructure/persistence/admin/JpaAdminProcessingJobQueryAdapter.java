package com.blaie.blaie_be.capture.infrastructure.persistence.admin;

import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobQueryPort;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAdminProcessingJobQueryAdapter implements AdminProcessingJobQueryPort {
    private final ProcessingJobRepository jobRepository;

    public JpaAdminProcessingJobQueryAdapter(ProcessingJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminProcessingJobResult> findJobs(
            ProcessingJobStatus status,
            boolean stuck,
            AdminProcessingJobCursor cursor,
            Instant now,
            int limit
    ) {
        return jobRepository.findAdminPage(
                        status == null ? null : status.value(),
                        stuck,
                        now,
                        cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.jobId(),
                        PageRequest.of(0, limit)
                ).stream()
                .map(AdminProcessingJobMapper::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminProcessingJobResult> findJob(UUID jobId) {
        return jobRepository.findById(jobId).map(AdminProcessingJobMapper::toResult);
    }
}
