package com.king.esb.admin.web.dto;

import com.king.esb.admin.persistence.entity.RouteRecord;

import java.time.Instant;

/** Route representation returned by the admin API. */
public record RouteResponse(
        String id,
        String name,
        String description,
        String path,
        String method,
        int priority,
        boolean enabled,
        String apiKey,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static RouteResponse of(RouteRecord record) {
        return new RouteResponse(record.getId(), record.getName(), record.getDescription(), record.getPath(),
                record.getMethod(), record.getPriority(), record.isEnabled(), record.getApiKey(),
                record.getVersion(), record.getCreatedAt(), record.getUpdatedAt());
    }
}
