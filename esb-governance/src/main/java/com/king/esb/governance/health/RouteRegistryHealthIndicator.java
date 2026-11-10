package com.king.esb.governance.health;

import com.king.esb.core.registry.RouteRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports the number of published routes; {@code UP} once at least one route is loaded. */
@Component
public class RouteRegistryHealthIndicator implements HealthIndicator {

    static final String COMPONENT_NAME = "esbRoutes";

    private final RouteRegistry routeRegistry;

    public RouteRegistryHealthIndicator(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @Override
    public Health health() {
        int count = routeRegistry.size();
        if (count > 0) {
            return Health.up().withDetail("publishedRoutes", count).build();
        }
        return Health.outOfService().withDetail("publishedRoutes", 0).build();
    }

    /** Actuator component name. */
    public String name() {
        return COMPONENT_NAME;
    }
}
