package com.king.esb.core.processor;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import org.springframework.stereotype.Component;

/** Removes a header from the target message; removing an absent header is a no-op. */
@Component
public class RemoveHeaderProcessor implements Processor {

    @Override
    public ProcessorType type() {
        return ProcessorType.REMOVE_HEADER;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        String name = definition.configString("name");
        if (name == null || name.isBlank()) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "REMOVE_HEADER requires a 'name'");
        }
        EsbMessage message = ProcessorSupport.target(exchange, definition.configString("applyTo"));
        message.removeHeader(name);
    }
}
