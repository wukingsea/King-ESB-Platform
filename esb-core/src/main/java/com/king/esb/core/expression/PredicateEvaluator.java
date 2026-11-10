package com.king.esb.core.expression;

import com.king.esb.core.route.PredicateDefinition;
import com.king.esb.core.route.PredicateOperator;

import java.util.List;
import java.util.regex.Pattern;

/** Evaluates {@link PredicateDefinition}s using the {@link ExpressionResolver}. */
public class PredicateEvaluator {

    private final ExpressionResolver resolver;

    public PredicateEvaluator(ExpressionResolver resolver) {
        this.resolver = resolver;
    }

    /** All predicates must match for the group to match; an empty group matches everything. */
    public boolean matchesAll(List<PredicateDefinition> predicates, ExpressionContext ctx) {
        if (predicates == null || predicates.isEmpty()) {
            return true;
        }
        for (PredicateDefinition predicate : predicates) {
            if (!matches(predicate, ctx)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(PredicateDefinition predicate, ExpressionContext ctx) {
        if (predicate == null) {
            return false;
        }
        String actual = resolver.evaluate(predicate.source(), ctx);
        String expected = predicate.value();
        PredicateOperator operator = predicate.operator();
        boolean present = actual != null;

        return switch (operator) {
            case EXISTS -> present;
            case EQ -> present && actual.equals(expected);
            case NE -> !present || !actual.equals(expected);
            case CONTAINS -> present && expected != null && actual.contains(expected);
            case STARTS_WITH -> present && expected != null && actual.startsWith(expected);
            case REGEX -> present && expected != null && Pattern.compile(expected).matcher(actual).find();
        };
    }
}
