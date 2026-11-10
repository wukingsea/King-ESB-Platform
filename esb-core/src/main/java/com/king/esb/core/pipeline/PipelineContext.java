package com.king.esb.core.pipeline;

import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.routing.ContentBasedRouter;
import com.king.esb.core.routing.LoadBalancer;
import com.king.esb.core.spi.OutboundClientRegistry;
import com.king.esb.core.spi.RateLimiter;
import com.king.esb.core.spi.TransformerRegistry;

/**
 * Dependency bundle handed to every {@link Processor}. Keeping this explicit (rather than injecting
 * many beans into each processor) keeps processors easy to unit test.
 */
public record PipelineContext(
        ExpressionResolver resolver,
        PredicateEvaluator predicateEvaluator,
        OutboundClientRegistry outboundClients,
        TransformerRegistry transformers,
        ContentBasedRouter router,
        LoadBalancer loadBalancer,
        RateLimiter rateLimiter) {

    public boolean hasRateLimiter() {
        return rateLimiter != null;
    }
}
