package com.king.esb.core.processor;

import com.king.esb.common.EsbConstants;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import org.springframework.stereotype.Component;

/** Enforces a fixed-window rate limit for the route; short-circuits with {@code 429} when exceeded. */
@Component
public class RateLimitProcessor implements Processor {

    @Override
    public ProcessorType type() {
        return ProcessorType.RATE_LIMIT;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        int limit = definition.configInt("requestsPerWindow", 0);
        int windowSeconds = definition.configInt("windowSeconds", 60);
        if (limit <= 0 || !context.hasRateLimiter()) {
            return;
        }
        String key = "esb:rl:" + exchange.routeId() + ":" + partitionKey(exchange, definition.configString("keyBy"));
        if (!context.rateLimiter().tryAcquire(key, limit, windowSeconds)) {
            exchange.status(ExchangeStatus.RATE_LIMITED);
            exchange.httpStatus(429);
            exchange.response(EsbMessage.empty());
        }
    }

    private String partitionKey(EsbExchange exchange, String keyBy) {
        String by = keyBy == null ? "ip" : keyBy;
        if ("ip".equalsIgnoreCase(by)) {
            return exchange.clientIp() == null ? "unknown" : exchange.clientIp();
        }
        if ("apikey".equalsIgnoreCase(by)) {
            String value = exchange.request().header(EsbConstants.API_KEY_HEADER);
            return value == null ? "none" : value;
        }
        String value = exchange.request().header(by);
        return value == null ? "none" : value;
    }
}
