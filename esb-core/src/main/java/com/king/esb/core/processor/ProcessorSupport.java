package com.king.esb.core.processor;

import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;

/** Shared helpers for processor implementations. */
final class ProcessorSupport {

    private ProcessorSupport() {
    }

    /**
     * Resolves the message a processor should act on. {@code applyTo} may be {@code request},
     * {@code response}, or null/other meaning "the current message" (response if present, else request).
     */
    static EsbMessage target(EsbExchange exchange, String applyTo) throws EsbException {
        if ("request".equalsIgnoreCase(applyTo)) {
            return exchange.request();
        }
        if ("response".equalsIgnoreCase(applyTo)) {
            if (exchange.response() == null) {
                throw new EsbException(com.king.esb.common.EsbErrorCode.INTERNAL_ERROR,
                        "No response message available yet");
            }
            return exchange.response();
        }
        return exchange.response() != null ? exchange.response() : exchange.request();
    }
}
