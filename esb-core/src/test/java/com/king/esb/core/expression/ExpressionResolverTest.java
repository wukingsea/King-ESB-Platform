package com.king.esb.core.expression;

import com.king.esb.core.model.EsbMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExpressionResolverTest {

    private final ExpressionResolver resolver = new ExpressionResolver();

    private ExpressionContext ctx() {
        EsbMessage message = EsbMessage.text(
                "{\"customer\":{\"id\":\"C-1\",\"tier\":\"gold\"},\"items\":[{\"sku\":\"A\"}]}");
        message.setHeader("X-Tenant", "acme");
        return ExpressionContext.of("POST", "/orders/42", Map.of("env", "prod"), message);
    }

    @Test
    void resolvesPathMethodHeadersQueryAndBody() {
        ExpressionContext ctx = ctx();
        assertEquals("POST", resolver.resolve("${method}", ctx));
        assertEquals("/orders/42", resolver.resolve("${path}", ctx));
        assertEquals("acme", resolver.resolve("${headers.X-Tenant}", ctx));
        assertEquals("acme", resolver.resolve("${header:X-Tenant}", ctx));
        assertEquals("prod", resolver.resolve("${query.env}", ctx));
        assertEquals("prod", resolver.resolve("${query:env}", ctx));
        assertEquals("C-1", resolver.resolve("${body.customer.id}", ctx));
        assertEquals("A", resolver.resolve("${body.items[0].sku}", ctx));
    }

    @Test
    void mixesLiteralsAndCollapsesUnknownTokens() {
        ExpressionContext ctx = ctx();
        assertEquals("env=prod", resolver.resolve("env=${query.env}", ctx));
        assertEquals("", resolver.resolve("${nope}", ctx));
        assertEquals("x", resolver.resolve("${unknown}x", ctx));
        assertEquals("plain", resolver.resolve("plain", ctx));
    }

    @Test
    void nullTemplateYieldsNull() {
        assertNull(resolver.resolve(null, ctx()));
    }

    @Test
    void unterminatedTokenIsLeftIntact() {
        assertEquals("a${b", resolver.resolve("a${b", ctx()));
    }

    @Test
    void evaluateResolvesBareTokens() {
        ExpressionContext ctx = ctx();
        assertEquals("/orders/42", resolver.evaluate("path", ctx));
        assertEquals("gold", resolver.evaluate("body.customer.tier", ctx));
        assertNull(resolver.evaluate("body.missing.path", ctx));
        assertNull(resolver.evaluate(null, ctx));
    }

    @Test
    void bodyTokenWithoutJsonStillReturnsRawString() {
        EsbMessage raw = EsbMessage.text("not-json");
        ExpressionContext ctx = ExpressionContext.of("GET", "/x", Map.of(), raw);
        assertEquals("not-json", resolver.resolve("${body}", ctx));
        assertNull(resolver.evaluate("body.a.b", ctx));
    }
}
