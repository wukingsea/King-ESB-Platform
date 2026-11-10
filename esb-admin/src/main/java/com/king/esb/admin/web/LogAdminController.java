package com.king.esb.admin.web;

import com.king.esb.admin.persistence.entity.CallLogEntity;
import com.king.esb.admin.service.CallLogQueryService;
import com.king.esb.admin.web.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Call-log query endpoint with route/status/time filters. */
@RestController
@RequestMapping("/admin/api/v1/logs")
public class LogAdminController {

    private final CallLogQueryService queryService;

    public LogAdminController(CallLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
    public PageResponse<LogItem> query(
            @RequestParam(required = false) String routeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        Page<LogItem> page = queryService.search(routeId, status, from, to,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())).map(LogItem::of);
        return PageResponse.of(page);
    }

    /** Flattened call-log row for the console. */
    public record LogItem(String id, String traceId, String routeId, String method, String path,
                          String status, int httpStatus, long durationMs, String clientIp,
                          String errorCode, String errorMessage, Instant createdAt) {

        static LogItem of(CallLogEntity e) {
            return new LogItem(String.valueOf(e.getId()), e.getTraceId(), e.getRouteId(), e.getMethod(),
                    e.getPath(), e.getStatus(), e.getHttpStatus(), e.getDurationMs(), e.getClientIp(),
                    e.getErrorCode(), e.getErrorMessage(), e.getCreatedAt());
        }
    }
}
