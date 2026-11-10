package com.king.esb.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.TransformType;
import com.king.esb.core.spi.Transformer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Converts between JSON and XML. The direction is fixed per instance (JSON→XML or XML→JSON) so the
 * transformer can register under a single {@link TransformType}. JSON→XML wraps the payload in a
 * configurable root element; XML→JSON strips the document root so a value round-trips cleanly
 * (e.g. {@code <customer><id>C-1</id></customer>} → {@code {"id":"C-1"}}).
 */
public class JsonXmlTransformer implements Transformer {

    private static final XmlMapper XML = XmlMapper.builder().build();

    private final TransformType type;

    public JsonXmlTransformer(TransformType type) {
        if (type != TransformType.JSON_TO_XML && type != TransformType.XML_TO_JSON) {
            throw new IllegalArgumentException("JsonXmlTransformer only supports JSON_TO_XML/XML_TO_JSON");
        }
        this.type = type;
    }

    @Override
    public TransformType type() {
        return type;
    }

    @Override
    public EsbMessage transform(EsbMessage input, Map<String, Object> config, ExpressionContext ctx) {
        return switch (type) {
            case JSON_TO_XML -> jsonToXml(input, config);
            case XML_TO_JSON -> xmlToJson(input);
            default -> throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "Unsupported transform " + type);
        };
    }

    private EsbMessage jsonToXml(EsbMessage input, Map<String, Object> config) {
        String rootName = rootName(config);
        try {
            JsonNode node = JsonUtil.readTree(input.bodyAsString());
            Object value = JsonUtil.mapper().treeToValue(node, Object.class);
            String xml = XML.writer().withRootName(rootName).writeValueAsString(value);
            return new EsbMessage(xml.getBytes(StandardCharsets.UTF_8), "application/xml");
        } catch (EsbException e) {
            throw e;
        } catch (Exception e) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "JSON to XML failed: " + e.getMessage(), e);
        }
    }

    private EsbMessage xmlToJson(EsbMessage input) {
        try {
            // XmlMapper.readTree already drops the document root element, so the tree is the
            // inner value directly (no extra unwrap needed).
            JsonNode node = XML.readTree(input.body());
            String json = JsonUtil.mapper().writeValueAsString(node);
            return new EsbMessage(json.getBytes(StandardCharsets.UTF_8), "application/json");
        } catch (EsbException e) {
            throw e;
        } catch (Exception e) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR, "XML to JSON failed: " + e.getMessage(), e);
        }
    }

    private String rootName(Map<String, Object> config) {
        Object value = config.get("rootName");
        if (value == null) {
            value = config.get("root");
        }
        return value == null ? "root" : String.valueOf(value);
    }
}
