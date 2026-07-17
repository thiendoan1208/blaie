package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.api.response.AdminOutboxSummaryResponse;
import com.blaie.blaie_be.capture.api.response.AdminProcessingJobResponse;
import com.blaie.blaie_be.capture.application.CaptureAdminService;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobPageResult;
import com.blaie.blaie_be.core.response.ApiResponse;
import com.blaie.blaie_be.core.response.PageMeta;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCaptureOperationsController {
    private final CaptureAdminService service;

    public AdminCaptureOperationsController(CaptureAdminService service) {
        this.service = service;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<AdminProcessingJobResponse>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") String stuck,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") String limit
    ) {
        AdminProcessingJobPageResult page = service.jobs(status, stuck, cursor, limit);
        return ApiResponse.of(
                page.jobs().stream().map(AdminProcessingJobResponse::from).toList(),
                null,
                PageMeta.of(page.nextCursor(), page.hasMore(), page.limit())
        );
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<AdminProcessingJobResponse> job(@PathVariable String jobId) {
        return ApiResponse.of(AdminProcessingJobResponse.from(service.job(jobId)));
    }

    @PostMapping("/jobs/{jobId}/requeue")
    public ResponseEntity<ApiResponse<AdminProcessingJobResponse>> requeue(@PathVariable String jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(AdminProcessingJobResponse.from(service.requeue(jobId))));
    }

    @PostMapping("/jobs/{jobId}/mark-dead")
    public ApiResponse<AdminProcessingJobResponse> markDead(@PathVariable String jobId) {
        return ApiResponse.of(AdminProcessingJobResponse.from(service.markDead(jobId)));
    }

    @GetMapping("/outbox/summary")
    public ApiResponse<AdminOutboxSummaryResponse> outboxSummary() {
        return ApiResponse.of(AdminOutboxSummaryResponse.from(service.outboxSummary()));
    }
}
