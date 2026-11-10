package com.king.esb.core.registry;

import com.king.esb.core.route.RouteDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteRegistryTest {

    private final RouteRegistry registry = new RouteRegistry();

    private RouteDefinition route(String id, String path, String method, int priority, boolean enabled) {
        return new RouteDefinition(id, id, null, path, method, priority, enabled, null, null, List.of(), null);
    }

    @Test
    void matchesByMethodAndPath() {
        registry.publish(route("r1", "/orders", "POST", 0, true));

        assertTrue(registry.match("POST", "/orders").isPresent());
        assertEquals("r1", registry.match("POST", "/orders").get().id());
        assertTrue(registry.match("POST", "/other").isEmpty());
    }

    @Test
    void anyMethodRouteMatchesEveryMethod() {
        registry.publish(route("any", "/ping", RouteDefinition.ANY_METHOD, 0, true));

        assertTrue(registry.match("GET", "/ping").isPresent());
        assertTrue(registry.match("DELETE", "/ping").isPresent());
    }

    @Test
    void wildcardMatchesSubpathsButNotSiblingPrefixes() {
        registry.publish(route("w", "/api/**", RouteDefinition.ANY_METHOD, 0, true));

        assertTrue(registry.match("GET", "/api").isPresent());
        assertTrue(registry.match("GET", "/api/users/1").isPresent());
        assertTrue(registry.match("GET", "/apiX").isEmpty());
    }

    @Test
    void exactRouteWinsOverWildcard() {
        registry.publish(route("w", "/api/**", RouteDefinition.ANY_METHOD, 0, true));
        registry.publish(route("e", "/api/users", "GET", 0, true));

        assertEquals("e", registry.match("GET", "/api/users").get().id());
        assertEquals("w", registry.match("GET", "/api/other").get().id());
    }

    @Test
    void disabledRouteIsStoredButNeverMatched() {
        registry.publish(route("d", "/orders", "POST", 0, false));

        assertTrue(registry.match("POST", "/orders").isEmpty());
        assertEquals(1, registry.size());
    }

    @Test
    void removeReplaceAllAndLookupById() {
        registry.publish(route("r1", "/a", RouteDefinition.ANY_METHOD, 0, true));
        registry.publish(route("r2", "/b", RouteDefinition.ANY_METHOD, 0, true));
        assertEquals(2, registry.size());

        registry.remove("r1");
        assertEquals(1, registry.size());
        assertTrue(registry.match("GET", "/a").isEmpty());

        registry.replaceAll(List.of(route("r3", "/c", RouteDefinition.ANY_METHOD, 0, true)));
        assertEquals(1, registry.size());
        assertEquals("r3", registry.byId("r3").orElseThrow().id());
        assertTrue(registry.byId("missing").isEmpty());
        assertEquals(1, registry.routes().size());
    }

    @Test
    void longPrefixWildcardIsPreferred() {
        registry.publish(route("short", "/a/**", RouteDefinition.ANY_METHOD, 0, true));
        registry.publish(route("long", "/a/b/**", RouteDefinition.ANY_METHOD, 0, true));

        assertEquals("long", registry.match("GET", "/a/b/c").get().id());
        assertEquals("short", registry.match("GET", "/a/x").get().id());
    }
}
