package com.king.esb.common;

/**
 * Canonical error codes used across the ESB platform. Each code carries a suggested
 * HTTP status so that both the gateway and the admin API can map failures uniformly
 * without depending on Spring web types.
 */
public enum EsbErrorCode {

    VALIDATION_ERROR(400, "Request validation failed"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    UNAUTHORIZED(401, "Authentication required"),
    INVALID_CREDENTIALS(401, "Invalid username or password"),
    FORBIDDEN(403, "Insufficient permissions"),
    ROUTE_NOT_FOUND(404, "No route matched the request"),
    NOT_FOUND(404, "Resource not found"),
    ROUTE_NAME_EXISTS(409, "A route with the same name already exists"),
    CONFLICT(409, "Conflicting resource state"),
    RATE_LIMITED(429, "Rate limit exceeded"),
    GATEWAY_ERROR(502, "Upstream call failed"),
    TRANSFORM_ERROR(500, "Message transformation failed"),
    INTERNAL_ERROR(500, "Unexpected internal error");

    private final int httpStatus;
    private final String defaultMessage;

    EsbErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
