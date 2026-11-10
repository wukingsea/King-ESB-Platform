package com.king.esb.core.processor;

import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.TransformType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Applies a message transformation to the current message. */
@Component
public class TransformProcessor implements Processor {

    @Override
    public ProcessorType type() {
        return ProcessorType.TRANSFORM;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        String typeName = definition.configString("transformType");
        if (typeName == null) {
            typeName = definition.configString("type");
        }
        TransformType transformType = TransformType.valueOf(typeName.toUpperCase(Locale.ROOT));
        EsbMessage input = ProcessorSupport.target(exchange, null);
        EsbMessage output = context.transformers().get(transformType)
                .transform(input, definition.config(), com.king.esb.core.expression.ExpressionContext.of(exchange, input));
        if (exchange.response() != null) {
            exchange.response(output);
        } else {
            exchange.request(output);
        }
    }
}
