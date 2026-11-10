package com.king.esb.transform;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.expression.ExpressionResolver;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.TransformType;
import com.king.esb.core.spi.Transformer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Renders a {@code template} string, substituting {@code ${...}} placeholders from the message
 * (path, method, headers, query, body) via the {@link ExpressionResolver}.
 */
@Component
public class TemplateTransformer implements Transformer {

    private final ExpressionResolver resolver = new ExpressionResolver();

    @Override
    public TransformType type() {
        return TransformType.TEMPLATE;
    }

    @Override
    public EsbMessage transform(EsbMessage input, Map<String, Object> config, ExpressionContext ctx) {
        Object template = config.get("template");
        if (template == null) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "TEMPLATE transform requires a 'template' config");
        }
        String rendered = resolver.resolve(String.valueOf(template), ctx);
        String contentType = contentType(config);
        return new EsbMessage(rendered.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private String contentType(Map<String, Object> config) {
        Object value = config.get("contentType");
        return value == null ? "text/plain" : String.valueOf(value);
    }
}
