package com.king.esb.admin.persistence;

import com.king.esb.admin.persistence.entity.CallLogEntity;
import com.king.esb.admin.persistence.repository.CallLogRepository;
import com.king.esb.core.spi.CallLogRecord;
import com.king.esb.core.spi.CallLogSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists call logs asynchronously on a bounded executor so logging never blocks the gateway
 * response path. Persistence failures are logged and swallowed.
 */
@Component
public class JpaCallLogSink implements CallLogSink {

    private static final Logger log = LoggerFactory.getLogger(JpaCallLogSink.class);

    private final CallLogRepository repository;

    public JpaCallLogSink(CallLogRepository repository) {
        this.repository = repository;
    }

    @Async("callLogExecutor")
    @Override
    public void save(CallLogRecord record) {
        try {
            CallLogEntity entity = new CallLogEntity();
            entity.setTraceId(record.traceId());
            entity.setRouteId(record.routeId());
            entity.setMethod(record.method());
            entity.setPath(record.path());
            entity.setStatus(record.status());
            entity.setHttpStatus(record.httpStatus());
            entity.setDurationMs(record.durationMillis());
            entity.setClientIp(record.clientIp());
            entity.setErrorCode(record.errorCode());
            entity.setErrorMessage(record.errorMessage());
            entity.setCreatedAt(Instant.now());
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("Failed to persist call log for trace {}: {}", record.traceId(), e.getMessage());
        }
    }
}
