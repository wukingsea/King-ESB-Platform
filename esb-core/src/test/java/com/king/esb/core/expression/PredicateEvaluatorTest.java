package com.king.esb.core.expression;

import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.PredicateDefinition;
import com.king.esb.core.route.PredicateOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredicateEvaluatorTest {

    private final PredicateEvaluator evaluator = new PredicateEvaluator(new ExpressionResolver());

    private ExpressionContext ctx() {
        EsbMessage message = EsbMessage.text("{\"customer\":{\"tier\":\"gold\"}}");
        message.setHeader("X-Tenant", "acme");
        return ExpressionContext.of("POST", "/orders", Map.of("env", "prod"), message);
    }

    private PredicateDefinition predicate(String source, PredicateOperator operator, String value) {
        return new PredicateDefinition(source, operator, value);
    }

    @Test
    void eqAndNe() {
        assertTrue(evaluator.matches(predicate("method", PredicateOperator.EQ, "POST"), ctx()));
        assertFalse(evaluator.matches(predicate("method", PredicateOperator.EQ, "GET"), ctx()));
        assertTrue(evaluator.matches(predicate("method", PredicateOperator.NE, "GET"), ctx()));
        assertFalse(evaluator.matches(predicate("method", PredicateOperator.NE, "POST"), ctx()));
    }

    @Test
    void containsStartsWithRegexExists() {
        assertTrue(evaluator.matches(predicate("body.customer.tier", PredicateOperator.CONTAINS, "ol"), ctx()));
        assertTrue(evaluator.matches(predicate("path", PredicateOperator.STARTS_WITH, "/ord"), ctx()));
        assertTrue(evaluator.matches(predicate("path", PredicateOperator.REGEX, "/orders?$"), ctx()));
        assertTrue(evaluator.matches(predicate("header:X-Tenant", PredicateOperator.EXISTS, null), ctx()));
        assertFalse(evaluator.matches(predicate("header:Missing", PredicateOperator.EXISTS, null), ctx()));
    }

    @Test
    void missingSourceDoesNotMatchValueOperators() {
        assertFalse(evaluator.matches(predicate("header:Missing", PredicateOperator.EQ, "x"), ctx()));
        assertFalse(evaluator.matches(predicate("header:Missing", PredicateOperator.CONTAINS, "x"), ctx()));
        assertTrue(evaluator.matches(predicate("header:Missing", PredicateOperator.NE, "x"), ctx()));
    }

    @Test
    void defaultOperatorIsExists() {
        PredicateDefinition def = predicate("header:X-Tenant", null, null);
        assertTrue(evaluator.matches(def, ctx()));
    }

    @Test
    void emptyGroupMatchesEverything() {
        assertTrue(evaluator.matchesAll(List.of(), ctx()));
        assertTrue(evaluator.matchesAll(null, ctx()));
    }

    @Test
    void groupRequiresAllPredicates() {
        List<PredicateDefinition> matching = List.of(
                predicate("method", PredicateOperator.EQ, "POST"),
                predicate("header:X-Tenant", PredicateOperator.EXISTS, null));
        assertTrue(evaluator.matchesAll(matching, ctx()));

        List<PredicateDefinition> failing = List.of(
                predicate("method", PredicateOperator.EQ, "GET"),
                predicate("header:X-Tenant", PredicateOperator.EXISTS, null));
        assertFalse(evaluator.matchesAll(failing, ctx()));
    }

    @Test
    void nullPredicateNeverMatches() {
        assertFalse(evaluator.matches(null, ctx()));
    }
}
