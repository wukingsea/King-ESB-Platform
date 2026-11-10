package com.king.esb.core.registry;

import com.king.esb.core.route.RouteDefinition;

/** A validated, runtime-ready route wrapping its {@link RouteDefinition}. */
public record CompiledRoute(RouteDefinition definition) {

    public String id() {
        return definition.id();
    }

    public boolean isWildcard() {
        return definition.path().endsWith("/**");
    }

    /** For {@code /orders/**} returns {@code /orders}. */
    public String prefix() {
        return definition.path().substring(0, definition.path().length() - 3);
    }

    public boolean matchesMethod(String method) {
        return definition.matchesAnyMethod() || definition.method().equalsIgnoreCase(method);
    }

    public boolean matchesPath(String path) {
        if (isWildcard()) {
            String prefix = prefix();
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return definition.path().equals(path);
    }

    public int priority() {
        return definition.priority();
    }
}
