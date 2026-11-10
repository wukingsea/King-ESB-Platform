package com.king.esb.transport.http;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOutboundClientTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger calls = new AtomicInteger();

    private final HttpOutboundClient client = new HttpOutboundClient(new JdkHttpTransport());

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "application/json", "{\"ok\":true}", Map.of("X-Backend", "stub"));
        });
        server.createContext("/fail", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "text/plain", "boom", Map.of());
        });
        server.createContext("/content-type", exchange -> {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            respond(exchange, 200, "text/plain", String.valueOf(contentType), Map.of());
        });
        server.start();
        port = server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType,
                                String body, Map<String, String> headers) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private EndpointDefinition endpoint(String path, int retry) {
        return new EndpointDefinition("e", "http://127.0.0.1:" + port + path, EndpointType.HTTP, "POST",
                Map.of(), 1000, 2000, retry, null);
    }

    private EsbExchange exchange() {
        EsbExchange exchange = new EsbExchange("trace", "r1", "POST", "/x", Map.of(), "127.0.0.1");
        exchange.request(EsbMessage.text("{}"));
        return exchange;
    }

    @Test
    void mapsStatusContentTypeAndSafeResponseHeaders() {
        calls.set(0);
        EsbMessage response = client.invoke(endpoint("/ok", 0), EsbMessage.text("{}"), exchange());

        assertEquals("{\"ok\":true}", response.bodyAsString());
        assertEquals("application/json", response.header("Content-Type"));
        assertEquals("stub", response.header("X-Backend"));
        assertEquals(1, calls.get());
    }

    @Test
    void serverErrorRetriesThenThrowsGatewayError() {
        calls.set(0);
        EsbException error = assertThrows(EsbException.class,
                () -> client.invoke(endpoint("/fail", 1), EsbMessage.text("{}"), exchange()));

        assertEquals(EsbErrorCode.GATEWAY_ERROR, error.code());
        assertEquals(2, calls.get());
    }

    @Test
    void forwardsRequestContentType() {
        EsbMessage request = new EsbMessage("{}".getBytes(StandardCharsets.UTF_8), "application/json");
        EsbMessage response = client.invoke(endpoint("/content-type", 0), request, exchange());
        assertTrue(response.bodyAsString().startsWith("application/json"));
    }

    @Test
    void unreachableHostThrowsGatewayError() {
        EndpointDefinition dead = new EndpointDefinition("e", "http://127.0.0.1:1/nope", EndpointType.HTTP,
                "POST", Map.of(), 500, 1000, 0, null);
        EsbException error = assertThrows(EsbException.class,
                () -> client.invoke(dead, EsbMessage.text("{}"), exchange()));
        assertEquals(EsbErrorCode.GATEWAY_ERROR, error.code());
    }
}
