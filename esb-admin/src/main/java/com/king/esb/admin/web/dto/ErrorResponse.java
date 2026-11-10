package com.king.esb.admin.web.dto;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;

import java.util.List;

/** Uniform error envelope returned by the admin API and the gateway. */
public record ErrorResponse(Error error) {

    public record Error(String code, String message, List<Detail> details, String traceId) {
    }

    public record Detail(String field, String message) {
    }

    public static ErrorResponse of(EsbErrorCode code, String message, List<EsbException.FieldError> details,
                                   String traceId) {
        List<Detail> mapped = details == null ? List.of()
                : details.stream().map(d -> new Detail(d.field(), d.message())).toList();
        return new ErrorResponse(new Error(code.name(), message, mapped, traceId));
    }

    public static ErrorResponse of(EsbException e, String traceId) {
        return of(e.code(), e.getMessage(), e.details(), traceId);
    }
}
