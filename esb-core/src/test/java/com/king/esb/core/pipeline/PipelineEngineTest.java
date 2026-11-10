package com.king.esb.core.pipeline;

import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.expression.PredicateEvaluator;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.processor.FilterProcessor;
import com.king.esb.core.processor.LogProcessor;
import com.king.esb.core.processor.RemoveHeaderProcessor;
import com.king.esb.core.processor.SetHeaderProcessor;
import com.king.esb.core.route.ErrorHandlerConfig;
import com.king.esb.core.route.ErrorStrategy;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.RouteDefinition;
import com.king.esb.core.routing.ContentBasedRouter;
import com.king.esb.core.routing.LoadBalancer;
import com.king.esb.core.spi.OutboundClientRegistry;
import com.king.esb.core.spi.TransformerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineEngineTest {

    private final ExpressionResolver resolver = new ExpressionResolver();
    private final PredicateEvaluator predicateEvaluator = new PredicateEvaluator(resolver);

    private PipelineContext context() {
        return new PipelineContext(resolver, predicateEvaluator,
                new OutboundClientRegistry(List.of()), new TransformerRegistry(List.of()),
                new ContentBasedRouter(predicateEvaluator), new LoadBalancer(), null);
    }

    private PipelineEngine engine() {
        ProcessorRegistry registry = new ProcessorRegistry(List.of(
                new LogProcessor(), new SetHeaderProcessor(), new RemoveHeaderProcessor(), new FilterProcessor()));
        return new PipelineEngine(registry);
    }

    private RouteDefinition route(List<ProcessorDefinition> pipeline, ErrorStrategy strategy) {
        return new RouteDefinition("r1", "r1", null, "/x", "POST", 0, true, null, null, pipeline,
                new ErrorHandlerConfig(strategy, 502));
    }

    private EsbExchange exchange() {
        EsbExchange exchange = new EsbExchange("trace-1", "r1", "POST", "/x", Map.of(), "127.0.0.1");
        exchange.request(EsbMessage.text("{\"a\":1}"));
        return exchange;
    }

    @Test
    void runsPipelineInOrderAndResolvesExpressions() {
        List<ProcessorDefinition> pipeline = List.of(
                new ProcessorDefinition("h1", ProcessorType.SET_HEADER, Map.of("name", "X-A", "value", "1")),
                new ProcessorDefinition("h2", ProcessorType.SET_HEADER, Map.of("name", "X-B", "value", "${path}")));

        EsbExchange exchange = exchange();
        engine().execute(route(pipeline, ErrorStrategy.DEFAULT), exchange, context());

        assertEquals(ExchangeStatus.OK, exchange.status());
        assertEquals("1", exchange.request().header("X-A"));
        assertEquals("/x", exchange.request().header("X-B"));
        assertNotNull(exchange.response());
        assertEquals(200, exchange.httpStatus());
    }

    @Test
    void echoesRequestWhenPipelineProducesNoResponse() {
        EsbExchange exchange = exchange();
        engine().execute(route(List.of(), ErrorStrategy.DEFAULT), exchange, context());

        assertNotNull(exchange.response());
        assertEquals("{\"a\":1}", exchange.response().bodyAsString());
    }

    @Test
    void filterShortCircuitsWithNoContentAndStopsPipeline() {
        List<ProcessorDefinition> pipeline = List.of(
                new ProcessorDefinition("f", ProcessorType.FILTER, Map.of("when",
                        List.of(Map.of("source", "method", "operator", "EQ", "value", "GET")))),
                new ProcessorDefinition("h1", ProcessorType.SET_HEADER, Map.of("name", "X-A", "value", "1")));

        EsbExchange exchange = exchange();
        engine().execute(route(pipeline, ErrorStrategy.DEFAULT), exchange, context());

        assertEquals(ExchangeStatus.FILTERED, exchange.status());
        assertEquals(204, exchange.httpStatus());
        assertFalse(exchange.request().hasHeader("X-A"));
    }

    @Test
    void defaultStrategyFailsFastAndReportsErrorStatus() {
        List<ProcessorDefinition> pipeline = List.of(
                new ProcessorDefinition("bad", ProcessorType.SET_HEADER, Map.of()),
                new ProcessorDefinition("h1", ProcessorType.SET_HEADER, Map.of("name", "X-A", "value", "1")));

        EsbExchange exchange = exchange();
        engine().execute(route(pipeline, ErrorStrategy.DEFAULT), exchange, context());

        assertEquals(ExchangeStatus.ERROR, exchange.status());
        assertEquals(400, exchange.httpStatus());
        assertFalse(exchange.request().hasHeader("X-A"));
    }

    @Test
    void continueStrategySwallowsFailureAndKeepsGoing() {
        List<ProcessorDefinition> pipeline = List.of(
                new ProcessorDefinition("bad", ProcessorType.SET_HEADER, Map.of()),
                new ProcessorDefinition("h1", ProcessorType.SET_HEADER, Map.of("name", "X-A", "value", "1")));

        EsbExchange exchange = exchange();
        engine().execute(route(pipeline, ErrorStrategy.CONTINUE), exchange, context());

        assertEquals(ExchangeStatus.OK, exchange.status());
        assertTrue(exchange.request().hasHeader("X-A"));
    }
}
