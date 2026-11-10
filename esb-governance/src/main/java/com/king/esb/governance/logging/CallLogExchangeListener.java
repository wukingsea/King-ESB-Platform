package com.king.esb.governance.logging;

import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.spi.CallLogRecord;
import com.king.esb.core.spi.CallLogSink;
import com.king.esb.core.spi.ExchangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps a completed exchange into a {@link CallLogRecord} and hands it to every registered
 * {@link CallLogSink}. Runs off the response path (see the async sink in {@code esb-admin}) and
 * never propagates failures back to the caller.
 */
@Component
public class CallLogExchangeListener implements ExchangeListener {

    private static final Logger log = LoggerFactory.getLogger(CallLogExchangeListener.class);

    private final List<CallLogSink> sinks;

    public CallLogExchangeListener(List<CallLogSink> sinks) {
        this.sinks = sinks;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void onExchange(EsbExchange exchange, int httpStatus) {
        if (sinks.isEmpty()) {
            return;
        }
        CallLogRecord record = toRecord(exchange, httpStatus);
        for (CallLogSink sink : sinks) {
            try {
                sink.save(record);
            } catch (RuntimeException e) {
                log.warn("Call log sink {} failed: {}", sink.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    CallLogRecord toRecord(EsbExchange exchange, int httpStatus) {
        String errorCode = null;
        String errorMessage = null;
        Throwable error = exchange.error();
        if (error != null) {
            errorMessage = error.getMessage();
            errorCode = error instanceof EsbException esb ? esb.code().name() : error.getClass().getSimpleName();
        }
        return new CallLogRecord(
                exchange.traceId(),
                exchange.routeId(),
                exchange.method(),
                exchange.path(),
                exchange.status() == null ? null : exchange.status().name(),
                httpStatus,
                Math.max(exchange.durationMillis(), 0),
                exchange.clientIp(),
                errorCode,
                errorMessage);
    }
}
