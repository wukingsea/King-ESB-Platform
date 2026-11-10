package com.king.esb.core.processor;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.TargetDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Invokes a routing target: selects endpoint candidates via the load balancer and calls the first
 * one that responds, failing over to the next on transport error.
 */
@Component
public class TargetInvoker {

    public void invoke(TargetDefinition target, EsbMessage outbound, EsbExchange exchange, PipelineContext context) {
        List<EndpointDefinition> candidates = context.loadBalancer().candidates(target);
        if (candidates.isEmpty()) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "Routing target has no endpoints configured");
        }
        EsbException last = null;
        for (EndpointDefinition endpoint : candidates) {
            try {
                EsbMessage response = context.outboundClients().invoke(endpoint, outbound, exchange);
                exchange.response(response);
                context.loadBalancer().reportSuccess(target);
                return;
            } catch (EsbException e) {
                last = e;
                context.loadBalancer().reportFailure(target);
            }
        }
        throw last != null ? last
                : new EsbException(EsbErrorCode.GATEWAY_ERROR, "All target endpoints failed");
    }

    /** Outbound message for a service call / router step. */
    public EsbMessage outboundMessage(EsbExchange exchange, ProcessorDefinition definition) {
        String source = definition.configString("source");
        return ProcessorSupport.target(exchange, source == null ? "request" : source);
    }
}
