package com.king.esb.core.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.TargetDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/** Content-based router: selects a target by predicate and invokes it. */
@Component
public class RouterProcessor implements Processor {

    private final TargetInvoker targetInvoker;

    public RouterProcessor(TargetInvoker targetInvoker) {
        this.targetInvoker = targetInvoker;
    }

    @Override
    public ProcessorType type() {
        return ProcessorType.ROUTER;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        Object rawTargets = definition.config().get("targets");
        if (rawTargets == null) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "ROUTER requires 'targets'");
        }
        List<TargetDefinition> targets = JsonUtil.mapper()
                .convertValue(rawTargets, new TypeReference<List<TargetDefinition>>() {
                });
        ExpressionContext ctx = ExpressionContext.of(exchange, targetInvoker.outboundMessage(exchange, definition));
        TargetDefinition selected = context.router().select(targets, ctx);
        if (selected == null) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "No routing target matched the message");
        }
        EsbMessage outbound = targetInvoker.outboundMessage(exchange, definition);
        targetInvoker.invoke(selected, outbound, exchange, context);
    }
}
