package com.king.esb.core.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;

import java.util.Map;

/**
 * Read-only view over an exchange used for expression resolution and predicate evaluation.
 * JSON parsing of the body is lazy so that non-JSON traffic pays no cost.
 */
public final class ExpressionContext {

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final EsbMessage message;

    private JsonNode parsedBody;
    private boolean bodyParsed;

    private ExpressionContext(String method, String path, Map<String, String> query, EsbMessage message) {
        this.method = method;
        this.path = path;
        this.query = query == null ? Map.of() : query;
        this.message = message == null ? EsbMessage.empty() : message;
    }

    public static ExpressionContext of(EsbExchange exchange) {
        return of(exchange, exchange.request());
    }

    public static ExpressionContext of(EsbExchange exchange, EsbMessage message) {
        return new ExpressionContext(exchange.method(), exchange.path(), exchange.query(), message);
    }

    public static ExpressionContext of(String method, String path, Map<String, String> query, EsbMessage message) {
        return new ExpressionContext(method, path, query, message);
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String bodyString() {
        return message == null ? "" : message.bodyAsString();
    }

    public String header(String name) {
        return message == null ? null : message.header(name);
    }

    public String queryParam(String name) {
        return query.get(name);
    }

    /** Returns the lazily-parsed JSON body, or {@code null} when the body is not valid JSON. */
    public JsonNode jsonBody() {
        if (!bodyParsed) {
            bodyParsed = true;
            String raw = bodyString();
            if (raw != null && !raw.isBlank()) {
                try {
                    JsonNode node = JsonUtil.readTree(raw);
                    parsedBody = node.isMissingNode() ? null : node;
                } catch (RuntimeException e) {
                    parsedBody = null;
                }
            }
        }
        return parsedBody;
    }
}
