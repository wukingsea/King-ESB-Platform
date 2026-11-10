package com.king.esb.admin.web;

import com.king.esb.admin.web.dto.ErrorResponse;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/** Translates exceptions raised by the admin API into the uniform error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EsbException.class)
    public ResponseEntity<ErrorResponse> handleEsbException(EsbException e) {
        log.debug("Request failed [{}]: {}", e.code(), e.getMessage());
        return ResponseEntity.status(e.httpStatus()).body(ErrorResponse.of(e, traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<EsbException.FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new EsbException.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        EsbException esb = EsbException.validation("Request validation failed", details);
        return ResponseEntity.status(esb.httpStatus()).body(ErrorResponse.of(esb, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        EsbException esb = new EsbException(EsbErrorCode.INTERNAL_ERROR, "Unexpected internal error", e);
        return ResponseEntity.status(esb.httpStatus()).body(ErrorResponse.of(esb, traceId()));
    }

    private String traceId() {
        String existing = MDC.get("traceId");
        return existing != null ? existing : UUID.randomUUID().toString();
    }
}
