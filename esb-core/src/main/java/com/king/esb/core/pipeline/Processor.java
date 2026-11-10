package com.king.esb.core.pipeline;

import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;

/**
 * A single, stateless pipeline step. Implementations are discovered by Spring and registered in
 * {@link ProcessorRegistry}.
 */
public interface Processor {

    ProcessorType type();

    /**
     * Applies this step to the exchange. Implementations must not swallow failures; they should throw
     * {@link EsbException} so the engine can apply the route's error strategy.
     */
    void process(EsbExchange exchange, ProcessorDefinition definition, PipelineContext context) throws EsbException;
}
