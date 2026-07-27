package com.blaie.blaie_be.inbox.api;

import com.blaie.blaie_be.core.response.ApiResponse;
import com.blaie.blaie_be.core.response.PageMeta;
import com.blaie.blaie_be.inbox.api.response.InboxItemResponse;
import com.blaie.blaie_be.inbox.application.InboxQueryService;
import com.blaie.blaie_be.inbox.application.result.InboxPageResult;
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
    private final InboxQueryService inboxQueryService;

    public InboxController(InboxQueryService inboxQueryService) {
        this.inboxQueryService = inboxQueryService;
    }

    @GetMapping
    public ApiResponse<List<InboxItemResponse>> inbox(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        InboxPageResult page = inboxQueryService.inbox(cursor, limit);
        return ApiResponse.of(
                page.items().stream().map(InboxItemResponse::from).toList(),
                null,
                PageMeta.of(page.nextCursor(), page.hasMore(), page.limit())
        );
    }

    @GetMapping("/items/{itemId}")
    public ApiResponse<InboxItemResponse> inboxItem(@PathVariable UUID itemId) {
        return ApiResponse.of(InboxItemResponse.from(inboxQueryService.inboxItem(itemId)));
    }
}
