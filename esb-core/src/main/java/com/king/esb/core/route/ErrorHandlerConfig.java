package com.king.esb.core.route;

/** Per-route error handling policy. */
public record ErrorHandlerConfig(ErrorStrategy strategy, int fallbackStatus) {

    public static ErrorHandlerConfig defaults() {
        return new ErrorHandlerConfig(ErrorStrategy.DEFAULT, 502);
    }

    public ErrorHandlerConfig {
        strategy = strategy == null ? ErrorStrategy.DEFAULT : strategy;
        fallbackStatus = fallbackStatus <= 0 ? 502 : fallbackStatus;
    }
}
