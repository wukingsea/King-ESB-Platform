package com.king.esb.admin.web.dto;

import java.util.List;

/** Pagination envelope for list endpoints. */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
                page.getNumber(), page.getSize());
    }
}
