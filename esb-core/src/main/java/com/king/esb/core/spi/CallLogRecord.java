package com.king.esb.core.spi;

/** Immutable snapshot of a processed exchange, ready for persistence. */
public record CallLogRecord(
        String traceId,
        String routeId,
        String method,
        String path,
        String status,
        int httpStatus,
        long durationMillis,
        String clientIp,
        String errorCode,
        String errorMessage) {
}
