package com.king.esb.core.spi;

import com.king.esb.core.model.EsbExchange;

/**
 * Observer notified after every completed exchange. Implementations (metrics, call logging) are
 * contribution points discovered by Spring; they must never throw and must not block the response path.
 */
public interface ExchangeListener {

    void onExchange(EsbExchange exchange, int httpStatus);

    default int order() {
        return 0;
    }
}
