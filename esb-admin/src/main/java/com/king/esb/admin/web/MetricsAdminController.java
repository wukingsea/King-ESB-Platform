package com.king.esb.admin.web;

import com.king.esb.admin.service.CallLogQueryService;
import com.king.esb.admin.service.CallLogQueryService.MetricsSummary;
import com.king.esb.core.registry.RouteRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aggregated metrics for the admin dashboard. */
@RestController
@RequestMapping("/admin/api/v1/metrics")
public class MetricsAdminController {

    private final CallLogQueryService queryService;
    private final RouteRegistry routeRegistry;

    public MetricsAdminController(CallLogQueryService queryService, RouteRegistry routeRegistry) {
        this.queryService = queryService;
        this.routeRegistry = routeRegistry;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
    public SummaryResponse summary() {
        MetricsSummary summary = queryService.summary();
        return new SummaryResponse(summary.totalCalls(), summary.errorCalls(), summary.errorRate(),
                summary.avgDurationMs(), routeRegistry.size());
    }

    /** Dashboard summary combining call aggregates and the live route count. */
    public record SummaryResponse(long totalCalls, long errorCalls, double errorRate,
                                  double avgDurationMs, int publishedRoutes) {
    }
}
