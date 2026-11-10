package com.king.esb.core.processor;

import com.king.esb.common.EsbConstants;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.routing.ContentBasedRouter;
import com.king.esb.core.routing.LoadBalancer;
import com.king.esb.core.spi.OutboundClientRegistry;
import com.king.esb.core.spi.RateLimiter;
import com.king.esb.core.spi.TransformerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorTest {

    private final ExpressionResolver resolver = new ExpressionResolver();
    private final PredicateEvaluator predicateEvaluator = new PredicateEvaluator(resolver);

    private PipelineContext context(RateLimiter rateLimiter) {
        return new PipelineContext(resolver, predicateEvaluator,
                new OutboundClientRegistry(List.of()), new TransformerRegistry(List.of()),
                new ContentBasedRouter(predicateEvaluator), new LoadBalancer(), rateLimiter);
    }

    private EsbExchange exchange() {
        EsbExchange exchange = new EsbExchange("trace-1", "r1", "POST", "/x", Map.of("env", "prod"), "10.0.0.1");
        EsbMessage request = EsbMessage.text("{}");
        request.setHeader("X-Old", "remove-me");
        exchange.request(request);
        return exchange;
    }

    @Test
    void setHeaderRequiresName() {
        SetHeaderProcessor processor = new SetHeaderProcessor();
        assertThrows(EsbException.class,
                () -> processor.process(exchange(), new ProcessorDefinition("p", ProcessorType.SET_HEADER, Map.of()),
                        context(null)));
    }

    @Test
    void setHeaderOverwritesExistingValue() {
        SetHeaderProcessor processor = new SetHeaderProcessor();
        EsbExchange exchange = exchange();
        exchange.request().setHeader("X-Tenant", "old");
        processor.process(exchange, new ProcessorDefinition("p", ProcessorType.SET_HEADER,
                Map.of("name", "X-Tenant", "value", "new")), context(null));
        assertEquals("new", exchange.request().header("X-Tenant"));
    }

    @Test
    void removeHeaderIsNoOpWhenAbsent() {
        RemoveHeaderProcessor processor = new RemoveHeaderProcessor();
        EsbExchange exchange = exchange();
        processor.process(exchange, new ProcessorDefinition("p", ProcessorType.REMOVE_HEADER,
                Map.of("name", "X-Old")), context(null));
        assertFalse(exchange.request().hasHeader("X-Old"));

        processor.process(exchange, new ProcessorDefinition("p2", ProcessorType.REMOVE_HEADER,
                Map.of("name", "X-Missing")), context(null));
        assertFalse(exchange.request().hasHeader("X-Missing"));
    }

    @Test
    void removeHeaderRequiresName() {
        RemoveHeaderProcessor processor = new RemoveHeaderProcessor();
        assertThrows(EsbException.class,
                () -> processor.process(exchange(), new ProcessorDefinition("p", ProcessorType.REMOVE_HEADER, Map.of()),
                        context(null)));
    }

    @Test
    void rateLimitShortCircuitsWhenExceeded() {
        AtomicInteger calls = new AtomicInteger();
        RateLimiter limiter = (key, limit, window) -> calls.getAndIncrement() < limit;
        RateLimitProcessor processor = new RateLimitProcessor();
        EsbExchange exchange = exchange();
        ProcessorDefinition definition = new ProcessorDefinition("rl", ProcessorType.RATE_LIMIT,
                Map.of("requestsPerWindow", 1, "windowSeconds", 60, "keyBy", "ip"));

        processor.process(exchange, definition, context(limiter));
        assertEquals(ExchangeStatus.OK, exchange.status());

        EsbExchange second = exchange();
        processor.process(second, definition, context(limiter));
        assertEquals(ExchangeStatus.RATE_LIMITED, second.status());
        assertEquals(429, second.httpStatus());
    }

    @Test
    void rateLimitSkipsWhenNoLimiterConfigured() {
        RateLimitProcessor processor = new RateLimitProcessor();
        EsbExchange exchange = exchange();
        processor.process(exchange, new ProcessorDefinition("rl", ProcessorType.RATE_LIMIT,
                Map.of("requestsPerWindow", 1)), context(null));
        assertEquals(ExchangeStatus.OK, exchange.status());
    }

    @Test
    void rateLimitPartitionsByHeader() {
        RateLimiter limiter = (key, limit, window) -> key.contains("acme");
        RateLimitProcessor processor = new RateLimitProcessor();
        EsbExchange exchange = exchange();
        exchange.request().setHeader(EsbConstants.API_KEY_HEADER, "acme");
        processor.process(exchange, new ProcessorDefinition("rl", ProcessorType.RATE_LIMIT,
                Map.of("requestsPerWindow", 5, "keyBy", "apikey")), context(limiter));
        assertEquals(ExchangeStatus.OK, exchange.status());

        EsbExchange other = exchange();
        other.request().setHeader(EsbConstants.API_KEY_HEADER, "other");
        processor.process(other, new ProcessorDefinition("rl", ProcessorType.RATE_LIMIT,
                Map.of("requestsPerWindow", 5, "keyBy", "apikey")), context(limiter));
        assertEquals(ExchangeStatus.RATE_LIMITED, other.status());
    }

    @Test
    void logProcessorDoesNotMutateExchange() {
        LogProcessor processor = new LogProcessor();
        EsbExchange exchange = exchange();
        processor.process(exchange, new ProcessorDefinition("log", ProcessorType.LOG,
                Map.of("level", "debug", "message", "path=${path} env=${query.env}")), context(null));
        assertEquals(ExchangeStatus.OK, exchange.status());
        assertTrue(exchange.request().hasHeader("X-Old"));
    }
}
