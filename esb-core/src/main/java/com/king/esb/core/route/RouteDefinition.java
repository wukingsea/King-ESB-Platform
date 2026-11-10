package com.king.esb.core.route;

import java.util.List;

/**
 * Declarative definition of a route. A route matches inbound requests by {@code method} + {@code path},
 * then executes {@code pipeline}. The definition is the unit that is persisted (as JSON) and compiled
 * into the runtime registry on publish.
 */
public record RouteDefinition(
        String id,
        String name,
        String description,
        String path,
        String method,
        int priority,
        boolean enabled,
        String apiKey,
        RateLimitConfig rateLimit,
        List<ProcessorDefinition> pipeline,
        ErrorHandlerConfig errorHandler) {

    public static final String ANY_METHOD = "*";

    public RouteDefinition {
        method = (method == null || method.isBlank()) ? ANY_METHOD : method.toUpperCase();
        pipeline = pipeline == null ? List.of() : List.copyOf(pipeline);
        errorHandler = errorHandler == null ? ErrorHandlerConfig.defaults() : errorHandler;
    }

    public boolean matchesAnyMethod() {
        return ANY_METHOD.equals(method);
    }
}
