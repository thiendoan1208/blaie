package com.blaie.blaie_be.audit.api;

import com.blaie.blaie_be.audit.api.response.AuditEventResponse;
import com.blaie.blaie_be.audit.application.AuditQueryService;
import com.blaie.blaie_be.audit.application.result.AuditEventPageResult;
import com.blaie.blaie_be.core.response.ApiResponse;
import com.blaie.blaie_be.core.response.PageMeta;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AdminAuditController {
    private final AuditQueryService service;

    public AdminAuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AuditEventResponse>> events(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") String limit
    ) {
        AuditEventPageResult page = service.events(cursor, limit);
        return ApiResponse.of(
                page.events().stream().map(AuditEventResponse::from).toList(),
                null,
                PageMeta.of(page.nextCursor(), page.hasMore(), page.limit())
        );
    }
}
