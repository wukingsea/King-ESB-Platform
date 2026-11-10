package com.king.esb.core.processor;

import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.Processor;
import com.king.esb.core.route.PredicateDefinition;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.king.esb.common.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Short-circuits the exchange with {@code 204 No Content} when the configured predicate group
 * does not match.
 */
@Component
public class FilterProcessor implements Processor {

    @Override
    public ProcessorType type() {
        return ProcessorType.FILTER;
    }

    @Override
    public void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) {
        List<PredicateDefinition> predicates = predicates(definition);
        if (predicates.isEmpty()) {
            return;
        }
        ExpressionContext ctx = ExpressionContext.of(exchange, ProcessorSupport.target(exchange, null));
        if (!context.predicateEvaluator().matchesAll(predicates, ctx)) {
            exchange.status(ExchangeStatus.FILTERED);
            exchange.httpStatus(204);
            exchange.response(EsbMessage.empty());
        }
    }

    private List<PredicateDefinition> predicates(ProcessorDefinition definition) {
        Object raw = definition.config().get("when");
        if (raw == null) {
            return List.of();
        }
        return JsonUtil.mapper().convertValue(raw, new TypeReference<List<PredicateDefinition>>() {
        });
    }
}
