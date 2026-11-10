package com.king.esb.core.expression;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolves {@code ${...}} placeholders and predicate sources against an {@link ExpressionContext}.
 *
 * <p>Supported token grammar (shared by templates and predicates):
 * <ul>
 *   <li>{@code path}, {@code method} — request path / method</li>
 *   <li>{@code headers.<name>} or {@code header:<name>} — message header</li>
 *   <li>{@code query.<name>} or {@code query:<name>} — query parameter</li>
 *   <li>{@code body} — raw body string</li>
 *   <li>{@code body.<dot.path>} or {@code body:<dot.path>} — value inside the JSON body</li>
 * </ul>
 * Unknown tokens resolve to {@code null} rather than failing.
 */
public class ExpressionResolver {

    /** Replaces every {@code ${...}} occurrence in {@code template}. Null template yields null. */
    public String resolve(String template, ExpressionContext ctx) {
        if (template == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("${", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            String token = template.substring(start + 2, end).trim();
            String value = lookup(token, ctx);
            out.append(value == null ? "" : value);
            i = end + 1;
        }
        return out.toString();
    }

    /** Evaluates a bare token (no {@code ${}} wrapper), used by predicate sources. */
    public String evaluate(String token, ExpressionContext ctx) {
        return lookup(token == null ? null : token.trim(), ctx);
    }

    private String lookup(String token, ExpressionContext ctx) {
        if (token == null || token.isEmpty() || ctx == null) {
            return null;
        }
        if ("path".equals(token)) {
            return ctx.path();
        }
        if ("method".equals(token)) {
            return ctx.method();
        }
        if ("body".equals(token)) {
            return ctx.bodyString();
        }
        if (token.startsWith("headers.")) {
            return ctx.header(token.substring("headers.".length()));
        }
        if (token.startsWith("header:")) {
            return ctx.header(token.substring("header:".length()));
        }
        if (token.startsWith("query.")) {
            return ctx.queryParam(token.substring("query.".length()));
        }
        if (token.startsWith("query:")) {
            return ctx.queryParam(token.substring("query:".length()));
        }
        if (token.startsWith("body.")) {
            return navigate(ctx.jsonBody(), token.substring("body.".length()));
        }
        if (token.startsWith("body:")) {
            return navigate(ctx.jsonBody(), token.substring("body:".length()));
        }
        return null;
    }

    private String navigate(JsonNode root, String dotPath) {
        if (root == null || dotPath == null || dotPath.isBlank()) {
            return null;
        }
        JsonNode current = root;
        for (String segment : dotPath.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            int bracket = segment.indexOf('[');
            if (bracket >= 0 && segment.endsWith("]")) {
                String name = segment.substring(0, bracket);
                int index = Integer.parseInt(segment.substring(bracket + 1, segment.length() - 1));
                current = name.isEmpty() ? current.path(index) : current.path(name).path(index);
            } else {
                current = current.path(segment);
            }
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }
}
