package com.king.esb.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain exception carrying an {@link EsbErrorCode}. Used by every module so that error
 * semantics survive across the core engine, transports, transformers and admin API.
 */
public class EsbException extends RuntimeException {

    private final EsbErrorCode code;
    private final List<FieldError> details;

    public EsbException(EsbErrorCode code) {
        this(code, code.defaultMessage(), null, List.of());
    }

    public EsbException(EsbErrorCode code, String message) {
        this(code, message, null, List.of());
    }

    public EsbException(EsbErrorCode code, String message, Throwable cause) {
        this(code, message, cause, List.of());
    }

    public EsbException(EsbErrorCode code, String message, Throwable cause, List<FieldError> details) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static EsbException validation(String message, List<FieldError> details) {
        return new EsbException(EsbErrorCode.VALIDATION_ERROR, message, null, details);
    }

    public EsbErrorCode code() {
        return code;
    }

    public int httpStatus() {
        return code.httpStatus();
    }

    public List<FieldError> details() {
        return Collections.unmodifiableList(new ArrayList<>(details));
    }

    /** A single field-level validation error. */
    public record FieldError(String field, String message) {
    }
}
