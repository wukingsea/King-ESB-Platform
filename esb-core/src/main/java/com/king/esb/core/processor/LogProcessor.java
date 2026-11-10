package com.king.esb.core.processor;

import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Emits a structured log line with the trace id, route id and a resolved message. */
@Component
public class LogProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(LogProcessor.class);

    @Override
    public ProcessorType type() {
        return ProcessorType.LOG;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        String rawMessage = definition.configString("message");
        String message = context.resolver().resolve(rawMessage,
                ExpressionContext.of(exchange, ProcessorSupport.target(exchange, null)));
        String level = definition.configString("level");
        String rendered = "[trace=" + exchange.traceId() + ", route=" + exchange.routeId() + "] "
                + (message == null ? "" : message);
        switch (level == null ? "info" : level.toLowerCase(Locale.ROOT)) {
            case "debug" -> log.debug(rendered);
            case "warn" -> log.warn(rendered);
            case "error" -> log.error(rendered);
            default -> log.info(rendered);
        }
    }
}
