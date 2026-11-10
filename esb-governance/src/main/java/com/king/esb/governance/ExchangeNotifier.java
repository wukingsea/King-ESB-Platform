package com.king.esb.governance;

import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.spi.ExchangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Fans a completed exchange out to all {@link ExchangeListener}s in ascending {@code order()}.
 * Listener exceptions are isolated so observability can never break the request path.
 */
@Component
public class ExchangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ExchangeNotifier.class);

    private final List<ExchangeListener> listeners;

    public ExchangeNotifier(List<ExchangeListener> listeners) {
        this.listeners = listeners.stream()
                .sorted(Comparator.comparingInt(ExchangeListener::order))
                .toList();
    }

    public void notify(EsbExchange exchange, int httpStatus) {
        for (ExchangeListener listener : listeners) {
            try {
                listener.onExchange(exchange, httpStatus);
            } catch (RuntimeException e) {
                log.warn("Exchange listener {} failed: {}", listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
