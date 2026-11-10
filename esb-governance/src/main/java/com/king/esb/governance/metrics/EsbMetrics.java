package com.king.esb.governance.metrics;

import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.spi.ExchangeListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Records per-exchange metrics: a duration timer plus total/error counters, all tagged by route and
 * status. Registered as an {@link ExchangeListener} so the gateway path stays free of metrics code.
 */
@Component
public class EsbMetrics implements ExchangeListener {

    static final String DURATION = "esb.exchange.duration";
    static final String TOTAL = "esb.exchange.total";
    static final String ERRORS = "esb.exchange.errors";

    private final MeterRegistry registry;

    public EsbMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onExchange(EsbExchange exchange, int httpStatus) {
        String route = exchange.routeId() == null ? "unknown" : exchange.routeId();
        String status = exchange.status() == null ? "UNKNOWN" : exchange.status().name();

        Timer.builder(DURATION)
                .tag("route", route)
                .tag("status", status)
                .register(registry)
                .record(Duration.ofMillis(Math.max(exchange.durationMillis(), 0)));

        Counter.builder(TOTAL)
                .tag("route", route)
                .tag("status", status)
                .register(registry)
                .increment();

        if (exchange.status() != ExchangeStatus.OK) {
            Counter.builder(ERRORS)
                    .tag("route", route)
                    .tag("status", status)
                    .register(registry)
                    .increment();
        }
    }
}
