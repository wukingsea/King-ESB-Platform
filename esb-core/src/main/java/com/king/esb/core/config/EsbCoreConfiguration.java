package com.king.esb.core.config;

import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.PipelineEngine;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.pipeline.ProcessorRegistry;
import com.king.esb.core.registry.RouteRegistry;
import com.king.esb.core.routing.ContentBasedRouter;
import com.king.esb.core.routing.LoadBalancer;
import com.king.esb.core.spi.OutboundClient;
import com.king.esb.core.spi.OutboundClientRegistry;
import com.king.esb.core.spi.RateLimiter;
import com.king.esb.core.spi.Transformer;
import com.king.esb.core.spi.TransformerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Wires the core engine beans. Processors/transports/transformers are discovered via component scan. */
@Configuration
public class EsbCoreConfiguration {

    @Bean
    public ExpressionResolver expressionResolver() {
        return new ExpressionResolver();
    }

    @Bean
    public PredicateEvaluator predicateEvaluator(ExpressionResolver expressionResolver) {
        return new PredicateEvaluator(expressionResolver);
    }

    @Bean
    public ContentBasedRouter contentBasedRouter(PredicateEvaluator predicateEvaluator) {
        return new ContentBasedRouter(predicateEvaluator);
    }

    @Bean
    public LoadBalancer loadBalancer() {
        return new LoadBalancer();
    }

    @Bean
    public ProcessorRegistry processorRegistry(List<Processor> processors) {
        return new ProcessorRegistry(processors);
    }

    @Bean
    public PipelineEngine pipelineEngine(ProcessorRegistry processorRegistry) {
        return new PipelineEngine(processorRegistry);
    }

    @Bean
    public OutboundClientRegistry outboundClientRegistry(List<OutboundClient> outboundClients) {
        return new OutboundClientRegistry(outboundClients);
    }

    @Bean
    public TransformerRegistry transformerRegistry(List<Transformer> transformers) {
        return new TransformerRegistry(transformers);
    }

    @Bean
    public RouteRegistry routeRegistry() {
        return new RouteRegistry();
    }

    @Bean
    public PipelineContext pipelineContext(ExpressionResolver expressionResolver,
                                           PredicateEvaluator predicateEvaluator,
                                           OutboundClientRegistry outboundClientRegistry,
                                           TransformerRegistry transformerRegistry,
                                           ContentBasedRouter contentBasedRouter,
                                           LoadBalancer loadBalancer,
                                           ObjectProvider<RateLimiter> rateLimiterProvider) {
        return new PipelineContext(expressionResolver, predicateEvaluator, outboundClientRegistry,
                transformerRegistry, contentBasedRouter, loadBalancer, rateLimiterProvider.getIfAvailable());
    }
}
