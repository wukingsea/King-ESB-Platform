package com.king.esb.core.routing;

import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.TargetDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces an ordered list of endpoint candidates for a target according to its strategy.
 * Callers try candidates in order and report success/failure so that {@code FAILOVER} can
 * advance its active index.
 */
public class LoadBalancer {

    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failoverIndexes = new ConcurrentHashMap<>();

    public List<EndpointDefinition> candidates(TargetDefinition target) {
        List<EndpointDefinition> endpoints = target.endpoints();
        if (endpoints.isEmpty()) {
            return List.of();
        }
        String key = key(target);
        return switch (target.strategy()) {
            case ROUND_ROBIN -> rotate(endpoints,
                    roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger()).getAndIncrement());
            case RANDOM -> shuffled(endpoints);
            case FAILOVER -> rotate(endpoints,
                    failoverIndexes.computeIfAbsent(key, k -> new AtomicInteger()).get());
        };
    }

    public void reportSuccess(TargetDefinition target) {
        if (target.strategy() == com.king.esb.core.route.LoadBalanceStrategy.FAILOVER) {
            failoverIndexes.computeIfAbsent(key(target), k -> new AtomicInteger()).set(0);
        }
    }

    public void reportFailure(TargetDefinition target) {
        if (target.strategy() == com.king.esb.core.route.LoadBalanceStrategy.FAILOVER) {
            int size = target.endpoints().size();
            if (size > 0) {
                failoverIndexes.computeIfAbsent(key(target), k -> new AtomicInteger())
                        .updateAndGet(current -> (current + 1) % size);
            }
        }
    }

    private String key(TargetDefinition target) {
        return target.id() != null ? target.id() : Integer.toHexString(System.identityHashCode(target));
    }

    private List<EndpointDefinition> rotate(List<EndpointDefinition> endpoints, int offset) {
        int size = endpoints.size();
        int start = Math.floorMod(offset, size);
        List<EndpointDefinition> rotated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rotated.add(endpoints.get((start + i) % size));
        }
        return rotated;
    }

    private List<EndpointDefinition> shuffled(List<EndpointDefinition> endpoints) {
        List<EndpointDefinition> copy = new ArrayList<>(endpoints);
        java.util.Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy;
    }
}
