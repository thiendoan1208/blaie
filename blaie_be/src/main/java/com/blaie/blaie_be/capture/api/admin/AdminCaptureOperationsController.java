package com.blaie.blaie_be.capture.api.admin;

import com.blaie.blaie_be.capture.api.admin.request.AdminMarkDeadRequest;
import com.blaie.blaie_be.capture.api.admin.response.AdminOutboxSummaryResponse;
import com.blaie.blaie_be.capture.api.admin.response.AdminProcessingJobResponse;
import com.blaie.blaie_be.capture.application.admin.CaptureJobAdminService;
import com.blaie.blaie_be.capture.application.admin.CaptureOutboxAdminService;
import com.blaie.blaie_be.capture.application.admin.query.AdminJobQuery;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.response.ApiResponse;
import com.blaie.blaie_be.core.response.PageMeta;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/capture")
public class AdminCaptureOperationsController {
    private static final int MAX_LIMIT = 100;

    private final CaptureJobAdminService jobService;
    private final CaptureOutboxAdminService outboxService;

    public AdminCaptureOperationsController(
            CaptureJobAdminService jobService,
            CaptureOutboxAdminService outboxService
    ) {
        this.jobService = jobService;
        this.outboxService = outboxService;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<AdminProcessingJobResponse>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") String stuck,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") String limit
    ) {
        AdminProcessingJobPageResult page = jobService.jobs(new AdminJobQuery(
                parseStatus(status),
                parseBoolean(stuck, "stuck"),
                cursor,
                parseLimit(limit)
        ));
        return ApiResponse.of(
                page.jobs().stream().map(AdminProcessingJobResponse::from).toList(),
                null,
                PageMeta.of(page.nextCursor(), page.hasMore(), page.limit())
        );
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<AdminProcessingJobResponse> job(@PathVariable String jobId) {
        return ApiResponse.of(AdminProcessingJobResponse.from(jobService.job(parseJobId(jobId))));
    }

    @PostMapping("/jobs/{jobId}/requeue")
    public ResponseEntity<ApiResponse<AdminProcessingJobResponse>> requeue(@PathVariable String jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(AdminProcessingJobResponse.from(jobService.requeue(parseJobId(jobId)))));
    }

    @PostMapping("/jobs/{jobId}/mark-dead")
    public ApiResponse<AdminProcessingJobResponse> markDead(
            @PathVariable String jobId,
            @Valid @RequestBody AdminMarkDeadRequest request
    ) {
        return ApiResponse.of(AdminProcessingJobResponse.from(
                jobService.markDead(parseJobId(jobId), request.reason())
        ));
    }

    @GetMapping("/outbox/summary")
    public ApiResponse<AdminOutboxSummaryResponse> outboxSummary() {
        return ApiResponse.of(AdminOutboxSummaryResponse.from(outboxService.summary()));
    }

    private ProcessingJobStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw validation("status is invalid");
        }
        try {
            return ProcessingJobStatus.fromValue(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("status is invalid");
        }
    }

    private boolean parseBoolean(String value, String field) {
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw validation(field + " must be true or false");
    }

    private int parseLimit(String value) {
        final int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw validation("limit must be an integer between 1 and " + MAX_LIMIT);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw validation("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private UUID parseJobId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw validation("jobId is invalid");
        }
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
}
