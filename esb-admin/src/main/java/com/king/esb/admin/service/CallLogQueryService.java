package com.king.esb.admin.service;

import com.king.esb.admin.persistence.entity.CallLogEntity;
import com.king.esb.admin.persistence.repository.CallLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Read model over persisted call logs plus aggregate metrics for the dashboard. */
@Service
public class CallLogQueryService {

    private final CallLogRepository repository;

    public CallLogQueryService(CallLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<CallLogEntity> search(String routeId, String status, Instant from, Instant to, Pageable pageable) {
        return repository.search(routeId, status, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public MetricsSummary summary() {
        long total = repository.totalCount();
        long errors = repository.countByStatusNot("OK");
        double avg = repository.averageDuration();
        double errorRate = total == 0 ? 0.0 : (double) errors / total;
        return new MetricsSummary(total, errors, errorRate, Math.round(avg * 100.0) / 100.0);
    }

    /** Aggregated call statistics for the admin dashboard. */
    public record MetricsSummary(long totalCalls, long errorCalls, double errorRate, double avgDurationMs) {
    }
}
