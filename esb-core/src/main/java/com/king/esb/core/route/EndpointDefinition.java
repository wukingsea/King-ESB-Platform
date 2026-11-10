package com.king.esb.core.route;

import java.util.Map;

/**
 * A concrete outbound destination. For {@link EndpointType#HTTP} the {@code uri} is a base or full
 * URL; additional request headers and timeouts/retries are applied by the outbound client.
 */
public record EndpointDefinition(
        String id,
        String uri,
        EndpointType type,
        String method,
        Map<String, String> headers,
        int connectTimeoutMs,
        int readTimeoutMs,
        int retryCount,
        String soapAction) {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10_000;

    public EndpointDefinition {
        type = type == null ? EndpointType.HTTP : type;
        method = (method == null || method.isBlank()) ? "POST" : method.toUpperCase();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        connectTimeoutMs = connectTimeoutMs <= 0 ? DEFAULT_CONNECT_TIMEOUT_MS : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs <= 0 ? DEFAULT_READ_TIMEOUT_MS : readTimeoutMs;
        retryCount = Math.max(retryCount, 0);
    }
}
