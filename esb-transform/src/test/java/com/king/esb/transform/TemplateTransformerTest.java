package com.king.esb.transform;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateTransformerTest {

    private final TemplateTransformer transformer = new TemplateTransformer();

    private ExpressionContext ctx() {
        return ExpressionContext.of("POST", "/orders",
                Map.of("env", "prod"), EsbMessage.text("{\"customer\":{\"id\":\"C-1\"}}"));
    }

    @Test
    void rendersPlaceholdersFromMessage() {
        EsbMessage out = transformer.transform(EsbMessage.empty(),
                Map.of("template", "id=${body.customer.id} path=${path} env=${query.env}"), ctx());

        assertEquals("text/plain", out.contentType());
        assertEquals("id=C-1 path=/orders env=prod", out.bodyAsString());
    }

    @Test
    void honoursConfiguredContentType() {
        EsbMessage out = transformer.transform(EsbMessage.empty(),
                Map.of("template", "{}", "contentType", "application/json"), ctx());

        assertEquals("application/json", out.contentType());
    }

    @Test
    void missingTemplateFails() {
        EsbException ex = assertThrows(EsbException.class,
                () -> transformer.transform(EsbMessage.empty(), Map.of(), ctx()));
        assertEquals(EsbErrorCode.TRANSFORM_ERROR, ex.code());
    }
}
