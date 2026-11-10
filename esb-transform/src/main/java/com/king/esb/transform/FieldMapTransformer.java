package com.king.esb.transform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.TransformType;
import com.king.esb.core.spi.Transformer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Builds a new JSON payload from a list of field {@code mappings}. Each mapping declares a
 * {@code target} field and a {@code source} expression (dot-path / header / query token), plus
 * optional {@code default} and {@code required} flags. A required source that resolves to nothing
 * fails the exchange with {@link EsbErrorCode#TRANSFORM_ERROR}.
 */
@Component
public class FieldMapTransformer implements Transformer {

    private final ExpressionResolver resolver = new ExpressionResolver();

    @Override
    public TransformType type() {
        return TransformType.FIELD_MAP;
    }

    @Override
    public EsbMessage transform(EsbMessage input, Map<String, Object> config, ExpressionContext ctx) {
        Object mappings = config.get("mappings");
        if (!(mappings instanceof List<?> list)) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "FIELD_MAP transform requires a 'mappings' list");
        }
        ObjectNode output = JsonUtil.mapper().createObjectNode();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> mapping)) {
                throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "Each mapping must be an object");
            }
            applyMapping(output, mapping, ctx);
        }
        return new EsbMessage(output.toString().getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void applyMapping(ObjectNode output, Map<?, ?> mapping, ExpressionContext ctx) {
        String target = str(mapping.get("target"));
        if (target == null || target.isBlank()) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "Mapping is missing 'target'");
        }
        String source = str(mapping.get("source"));
        String value = source == null ? null : resolver.evaluate(source, ctx);
        if (value == null || value.isEmpty()) {
            if (mapping.containsKey("default")) {
                value = str(mapping.get("default"));
            } else if (isRequired(mapping)) {
                throw new EsbException(EsbErrorCode.TRANSFORM_ERROR,
                        "Required field '" + target + "' missing from source '" + source + "'");
            }
        }
        if (value != null) {
            output.put(target, value);
        }
    }

    private boolean isRequired(Map<?, ?> mapping) {
        Object flag = mapping.get("required");
        return flag instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(flag));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
