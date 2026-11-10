package com.king.esb.core.routing;

import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.route.TargetDefinition;

import java.util.List;

/**
 * Selects a routing target by evaluating each target's predicate group in declaration order.
 * A target without predicates acts as the (last) fallback.
 */
public class ContentBasedRouter {

    private final PredicateEvaluator evaluator;

    public ContentBasedRouter(PredicateEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * @return the first target whose predicates all match, otherwise the fallback (predicate-less)
     *         target, or {@code null} when nothing matches.
     */
    public TargetDefinition select(List<TargetDefinition> targets, com.king.esb.core.expression.ExpressionContext ctx) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        TargetDefinition fallback = null;
        for (TargetDefinition target : targets) {
            if (target.endpoints().isEmpty()) {
                continue;
            }
            if (target.when() == null || target.when().isEmpty()) {
                fallback = target;
            } else if (evaluator.matchesAll(target.when(), ctx)) {
                return target;
            }
        }
        return fallback;
    }

    public PredicateEvaluator evaluator() {
        return evaluator;
    }
}
