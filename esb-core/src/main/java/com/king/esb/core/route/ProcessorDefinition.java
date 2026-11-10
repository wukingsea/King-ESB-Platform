package com.king.esb.core.route;

import java.util.Map;

/**
 * A single step in a route pipeline. {@code config} is processor-type specific and is validated
 * by the owning {@code Processor}.
 */
public record ProcessorDefinition(String id, ProcessorType type, Map<String, Object> config) {

    public ProcessorDefinition {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public String configString(String key) {
        Object v = config.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public boolean configBoolean(String key, boolean defaultValue) {
        Object v = config.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? defaultValue : Boolean.parseBoolean(String.valueOf(v));
    }

    public int configInt(String key, int defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return v == null ? defaultValue : Integer.parseInt(String.valueOf(v));
    }
}
