package com.king.esb.core.route;

import java.util.List;

/**
 * A routing target: a set of endpoints selected via a load-balancing strategy, guarded by optional
 * content-based predicates. A target with no predicates (or marked default) matches anything.
 */
public record TargetDefinition(
        String id,
        List<EndpointDefinition> endpoints,
        LoadBalanceStrategy strategy,
        List<PredicateDefinition> when,
        boolean isDefault) {

    public TargetDefinition {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        strategy = strategy == null ? LoadBalanceStrategy.ROUND_ROBIN : strategy;
        when = when == null ? List.of() : List.copyOf(when);
    }

    /** True when this target has no conditions and therefore acts as a fallback. */
    public boolean matchesAll() {
        return isDefault || when.isEmpty();
    }
}
