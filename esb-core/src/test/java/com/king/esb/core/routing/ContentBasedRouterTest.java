package com.king.esb.core.routing;

import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.core.route.PredicateDefinition;
import com.king.esb.core.route.PredicateOperator;
import com.king.esb.core.route.TargetDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentBasedRouterTest {

    private final ContentBasedRouter router =
            new ContentBasedRouter(new PredicateEvaluator(new ExpressionResolver()));

    private EndpointDefinition endpoint(String id) {
        return new EndpointDefinition(id, "http://localhost/" + id, EndpointType.HTTP, "POST", null, 0, 0, 0, null);
    }

    private ExpressionContext ctx(String tenant) {
        EsbMessage message = EsbMessage.text("{}");
        if (tenant != null) {
            message.setHeader("X-Tenant", tenant);
        }
        return ExpressionContext.of("POST", "/orders", Map.of(), message);
    }

    @Test
    void selectsFirstMatchingTarget() {
        TargetDefinition gold = new TargetDefinition("gold", List.of(endpoint("gold")), null,
                List.of(new PredicateDefinition("header:X-Tenant", PredicateOperator.EQ, "acme")), false);
        TargetDefinition fallback = new TargetDefinition("default", List.of(endpoint("default")), null, null, true);

        assertEquals("gold", router.select(List.of(gold, fallback), ctx("acme")).id());
        assertEquals("default", router.select(List.of(gold, fallback), ctx("other")).id());
    }

    @Test
    void returnsFallbackWhenNothingMatches() {
        TargetDefinition gold = new TargetDefinition("gold", List.of(endpoint("gold")), null,
                List.of(new PredicateDefinition("header:X-Tenant", PredicateOperator.EQ, "acme")), false);
        TargetDefinition fallback = new TargetDefinition("default", List.of(endpoint("default")), null, null, true);

        assertEquals("default", router.select(List.of(gold, fallback), ctx(null)).id());
    }

    @Test
    void returnsNullWhenNoMatchAndNoFallback() {
        TargetDefinition gold = new TargetDefinition("gold", List.of(endpoint("gold")), null,
                List.of(new PredicateDefinition("header:X-Tenant", PredicateOperator.EQ, "acme")), false);

        assertNull(router.select(List.of(gold), ctx("other")));
    }

    @Test
    void skipsTargetsWithoutEndpoints() {
        TargetDefinition empty = new TargetDefinition("empty", List.of(), null, null, true);
        TargetDefinition fallback = new TargetDefinition("default", List.of(endpoint("default")), null, null, true);

        assertEquals("default", router.select(List.of(empty, fallback), ctx(null)).id());
    }

    @Test
    void nullOrEmptyTargetListYieldsNull() {
        assertNull(router.select(null, ctx("acme")));
        assertNull(router.select(List.of(), ctx("acme")));
    }
}
