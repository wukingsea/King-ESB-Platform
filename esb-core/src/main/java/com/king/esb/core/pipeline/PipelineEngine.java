package com.king.esb.core.pipeline;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.route.ErrorStrategy;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a route's pipeline in order. Stops as soon as a processor short-circuits the exchange
 * (filtered / rate-limited), and applies the route error strategy on failure.
 */
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    private final ProcessorRegistry registry;

    public PipelineEngine(ProcessorRegistry registry) {
        this.registry = registry;
    }

    public void execute(RouteDefinition route, EsbExchange exchange, PipelineContext context) {
        for (ProcessorDefinition definition : route.pipeline()) {
            if (exchange.status() != ExchangeStatus.OK) {
                break;
            }
            Processor processor = registry.get(definition.type());
            try {
                processor.process(exchange, definition, context);
            } catch (EsbException e) {
                if (!handleFailure(route, exchange, e)) {
                    break;
                }
            } catch (RuntimeException e) {
                EsbException wrapped = new EsbException(EsbErrorCode.INTERNAL_ERROR,
                        "Processor " + definition.type() + " failed: " + e.getMessage(), e);
                if (!handleFailure(route, exchange, wrapped)) {
                    break;
                }
            }
        }
        if (exchange.status() == ExchangeStatus.OK && exchange.response() == null) {
            // A route without an outbound call echoes the request body back to the caller.
            exchange.response(exchange.request());
            exchange.httpStatus(200);
        }
    }

    /**
     * @return {@code true} if execution should continue, {@code false} if it should stop.
     */
    private boolean handleFailure(RouteDefinition route, EsbExchange exchange, EsbException e) {
        if (route.errorHandler().strategy() == ErrorStrategy.CONTINUE) {
            log.warn("Pipeline step failed but strategy=CONTINUE [route={}, trace={}]: {}",
                    route.name(), exchange.traceId(), e.getMessage());
            return true;
        }
        log.debug("Pipeline step failed [route={}, trace={}]: {}", route.name(), exchange.traceId(), e.getMessage());
        exchange.error(e);
        exchange.status(ExchangeStatus.ERROR);
        exchange.httpStatus(e.httpStatus());
        if (exchange.response() == null) {
            exchange.response(EsbMessage.empty());
        }
        return false;
    }
}
