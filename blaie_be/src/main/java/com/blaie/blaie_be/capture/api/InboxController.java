package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.api.response.CaptureItemResponse;
import com.blaie.blaie_be.capture.application.CaptureService;
import com.blaie.blaie_be.capture.application.result.InboxPageResult;
import com.blaie.blaie_be.core.response.ApiResponse;
import com.blaie.blaie_be.core.response.PageMeta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/inbox")
public class InboxController {
    private final CaptureService captureService;

    public InboxController(CaptureService captureService) {
        this.captureService = captureService;
    }

    @GetMapping
    public ApiResponse<List<CaptureItemResponse>> inbox(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        InboxPageResult page = captureService.inbox(cursor, limit);
        return ApiResponse.of(
                page.items().stream().map(CaptureItemResponse::from).toList(),
                null,
                PageMeta.of(page.nextCursor(), page.hasMore(), page.limit())
        );
    }

    @GetMapping("/items/{itemId}")
    public ApiResponse<CaptureItemResponse> inboxItem(@PathVariable UUID itemId) {
        return ApiResponse.of(CaptureItemResponse.from(captureService.inboxItem(itemId)));
    }
}
