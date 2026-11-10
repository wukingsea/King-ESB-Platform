package com.king.esb.core.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.TargetDefinition;
import org.springframework.stereotype.Component;

/** Invokes a single configured target (a target is a load-balanced pool of endpoints). */
@Component
public class ServiceCallProcessor implements Processor {

    private final TargetInvoker targetInvoker;

    public ServiceCallProcessor(TargetInvoker targetInvoker) {
        this.targetInvoker = targetInvoker;
    }

    @Override
    public ProcessorType type() {
        return ProcessorType.SERVICE_CALL;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        Object rawTarget = definition.config().get("target");
        if (rawTarget == null) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "SERVICE_CALL requires a 'target'");
        }
        TargetDefinition target = JsonUtil.mapper().convertValue(rawTarget, new TypeReference<TargetDefinition>() {
        });
        EsbMessage outbound = targetInvoker.outboundMessage(exchange, definition);
        targetInvoker.invoke(target, outbound, exchange, context);
    }
}
