package com.king.esb.transform;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.TransformType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonXmlTransformerTest {

    @Test
    void jsonToXmlWrapsPayloadInConfiguredRoot() {
        JsonXmlTransformer transformer = new JsonXmlTransformer(TransformType.JSON_TO_XML);

        EsbMessage out = transformer.transform(EsbMessage.text("{\"id\":\"C-1\"}"), Map.of("rootName", "customer"), null);

        assertEquals("application/xml", out.contentType());
        assertTrue(out.bodyAsString().contains("<customer>"));
        assertTrue(out.bodyAsString().contains("<id>C-1</id>"));
    }

    @Test
    void jsonToXmlUsesDefaultRootWhenUnconfigured() {
        JsonXmlTransformer transformer = new JsonXmlTransformer(TransformType.JSON_TO_XML);

        EsbMessage out = transformer.transform(EsbMessage.text("{\"id\":\"C-1\"}"), Map.of(), null);

        assertTrue(out.bodyAsString().contains("<root>"));
    }

    @Test
    void xmlToJsonUnwrapsSingleRoot() {
        JsonXmlTransformer transformer = new JsonXmlTransformer(TransformType.XML_TO_JSON);
        EsbMessage xml = new EsbMessage(
                "<customer><id>C-1</id></customer>".getBytes(StandardCharsets.UTF_8), "application/xml");

        EsbMessage out = transformer.transform(xml, Map.of(), null);

        assertEquals("application/json", out.contentType());
        assertEquals("{\"id\":\"C-1\"}", out.bodyAsString());
    }

    @Test
    void invalidXmlFailsWithTransformError() {
        JsonXmlTransformer transformer = new JsonXmlTransformer(TransformType.XML_TO_JSON);
        EsbMessage broken = new EsbMessage("<customer><id>".getBytes(StandardCharsets.UTF_8), "application/xml");

        EsbException ex = assertThrows(EsbException.class, () -> transformer.transform(broken, Map.of(), null));
        assertEquals(EsbErrorCode.TRANSFORM_ERROR, ex.code());
    }

    @Test
    void rejectsUnsupportedDirection() {
        assertThrows(IllegalArgumentException.class, () -> new JsonXmlTransformer(TransformType.TEMPLATE));
    }
}
