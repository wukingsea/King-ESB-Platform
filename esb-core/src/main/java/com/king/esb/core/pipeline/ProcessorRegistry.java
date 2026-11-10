package com.king.esb.core.pipeline;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.route.ProcessorType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Maps {@link ProcessorType} to its {@link Processor} implementation. */
public class ProcessorRegistry {

    private final Map<ProcessorType, Processor> processors = new EnumMap<>(ProcessorType.class);

    public ProcessorRegistry(List<Processor> processorList) {
        for (Processor processor : processorList) {
            Processor previous = processors.put(processor.type(), processor);
            if (previous != null) {
                throw new IllegalStateException("Duplicate processor for type " + processor.type());
            }
        }
    }

    public Processor get(ProcessorType type) {
        Processor processor = processors.get(type);
        if (processor == null) {
            throw new EsbException(EsbErrorCode.INTERNAL_ERROR, "No processor registered for type " + type);
        }
        return processor;
    }

    public boolean supports(ProcessorType type) {
        return processors.containsKey(type);
    }

    public java.util.Set<ProcessorType> registeredTypes() {
        return java.util.Set.copyOf(processors.keySet());
    }
}
