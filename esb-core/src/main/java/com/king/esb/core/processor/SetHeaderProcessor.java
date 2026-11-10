package com.king.esb.core.processor;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import org.springframework.stereotype.Component;

/** Sets (or overwrites) a header on the target message, resolving {@code ${...}} in the value. */
@Component
public class SetHeaderProcessor implements Processor {

    @Override
    public ProcessorType type() {
        return ProcessorType.SET_HEADER;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        String name = definition.configString("name");
        if (name == null || name.isBlank()) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "SET_HEADER requires a 'name'");
        }
        String rawValue = definition.configString("value");
        String value = context.resolver()
                .resolve(rawValue, ExpressionContext.of(exchange, ProcessorSupport.target(exchange, null)));
        EsbMessage message = ProcessorSupport.target(exchange, definition.configString("applyTo"));
        message.setHeader(name, value);
    }
}
