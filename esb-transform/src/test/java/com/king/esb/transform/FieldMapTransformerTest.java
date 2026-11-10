package com.king.esb.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldMapTransformerTest {

    private final FieldMapTransformer transformer = new FieldMapTransformer();

    private ExpressionContext ctx(String body) {
        return ExpressionContext.of("POST", "/orders", Map.of(), EsbMessage.text(body));
    }

    @Test
    void mapsFieldsAndAppliesDefaults() {
        List<Map<String, Object>> mappings = List.of(
                Map.of("target", "id", "source", "body.customer.id"),
                Map.of("target", "amount", "source", "body.total", "default", "0"),
                Map.of("target", "channel", "source", "header:X-Channel", "default", "web"));

        EsbMessage out = transformer.transform(EsbMessage.empty(), Map.of("mappings", mappings),
                ctx("{\"customer\":{\"id\":\"C-1\"},\"total\":10}"));

        assertEquals("application/json", out.contentType());
        JsonNode node = JsonUtil.readTree(out.bodyAsString());
        assertEquals("C-1", node.get("id").asText());
        assertEquals("10", node.get("amount").asText());
        assertEquals("web", node.get("channel").asText());
    }

    @Test
    void requiredMissingSourceFails() {
        List<Map<String, Object>> mappings = List.of(
                Map.of("target", "id", "source", "body.customer.id", "required", true));

        EsbException ex = assertThrows(EsbException.class,
                () -> transformer.transform(EsbMessage.empty(), Map.of("mappings", mappings), ctx("{}")));
        assertEquals(EsbErrorCode.TRANSFORM_ERROR, ex.code());
    }

    @Test
    void optionalMissingSourceIsOmitted() {
        List<Map<String, Object>> mappings = List.of(
                Map.of("target", "id", "source", "body.customer.id"));

        EsbMessage out = transformer.transform(EsbMessage.empty(), Map.of("mappings", mappings), ctx("{}"));

        assertEquals("{}", out.bodyAsString());
    }

    @Test
    void missingMappingsListFails() {
        EsbException ex = assertThrows(EsbException.class,
                () -> transformer.transform(EsbMessage.empty(), Map.of(), ctx("{}")));
        assertEquals(EsbErrorCode.TRANSFORM_ERROR, ex.code());
    }

    @Test
    void mappingWithoutTargetFails() {
        List<Map<String, Object>> mappings = List.of(Map.of("source", "body.customer.id"));

        EsbException ex = assertThrows(EsbException.class,
                () -> transformer.transform(EsbMessage.empty(), Map.of("mappings", mappings), ctx("{}")));
        assertEquals(EsbErrorCode.TRANSFORM_ERROR, ex.code());
    }
}
