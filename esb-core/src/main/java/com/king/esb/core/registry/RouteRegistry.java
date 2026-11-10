package com.king.esb.core.registry;

import com.king.esb.core.route.RouteDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory, hot-swappable registry of compiled routes. All reads are lock-free; writes swap an
 * immutable snapshot via {@link AtomicReference}, so matching never blocks and never sees a partial
 * update.
 */
public class RouteRegistry {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    /** Publishes (adds or replaces) a single route. */
    public void publish(RouteDefinition definition) {
        snapshot.updateAndGet(current -> {
            Map<String, CompiledRoute> byId = new LinkedHashMap<>(current.byId);
            byId.put(definition.id(), new CompiledRoute(definition));
            return Snapshot.of(byId);
        });
    }

    public void remove(String routeId) {
        snapshot.updateAndGet(current -> {
            if (!current.byId.containsKey(routeId)) {
                return current;
            }
            Map<String, CompiledRoute> byId = new LinkedHashMap<>(current.byId);
            byId.remove(routeId);
            return Snapshot.of(byId);
        });
    }

    /** Replaces the entire registry contents (used on startup load). */
    public void replaceAll(List<RouteDefinition> definitions) {
        Map<String, CompiledRoute> byId = new LinkedHashMap<>();
        definitions.forEach(d -> byId.put(d.id(), new CompiledRoute(d)));
        snapshot.set(Snapshot.of(byId));
    }

    public Optional<CompiledRoute> match(String method, String path) {
        Snapshot current = snapshot.get();
        CompiledRoute exact = current.exact.get(method + " " + path);
        if (exact == null) {
            exact = current.exact.get("* " + path);
        }
        if (exact != null) {
            return Optional.of(exact);
        }
        for (CompiledRoute candidate : current.wildcard) {
            if (candidate.matchesMethod(method) && candidate.matchesPath(path)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<CompiledRoute> byId(String routeId) {
        return Optional.ofNullable(snapshot.get().byId.get(routeId));
    }

    public List<RouteDefinition> routes() {
        return snapshot.get().byId.values().stream().map(CompiledRoute::definition).toList();
    }

    public int size() {
        return snapshot.get().byId.size();
    }

    /** Immutable lookup snapshot derived from the route map. */
    private record Snapshot(
            Map<String, CompiledRoute> byId,
            Map<String, CompiledRoute> exact,
            List<CompiledRoute> wildcard) {

        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), List.of());
        }

        static Snapshot of(Map<String, CompiledRoute> byId) {
            Map<String, CompiledRoute> exact = new LinkedHashMap<>();
            List<CompiledRoute> wildcard = new ArrayList<>();
            List<CompiledRoute> all = new ArrayList<>(byId.values());
            all.sort(Comparator.comparingInt(CompiledRoute::priority).reversed());
            for (CompiledRoute route : all) {
                if (!route.definition().enabled()) {
                    continue;
                }
                if (route.isWildcard()) {
                    wildcard.add(route);
                } else {
                    exact.put(route.definition().method() + " " + route.definition().path(), route);
                    exact.putIfAbsent("* " + route.definition().path(), route);
                }
            }
            wildcard.sort(Comparator
                    .comparingInt((CompiledRoute r) -> r.prefix().length()).reversed()
                    .thenComparing(Comparator.comparingInt(CompiledRoute::priority).reversed()));
            return new Snapshot(Map.copyOf(byId), Map.copyOf(exact), List.copyOf(wildcard));
        }
    }
}
