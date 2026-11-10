package com.king.esb.transport.soap;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.transport.http.JdkHttpTransport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapOutboundClientTest {

    private static final String NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private static HttpServer server;
    private static int port;
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private static final AtomicReference<String> lastSoapAction = new AtomicReference<>();

    private final SoapOutboundClient client =
            new SoapOutboundClient(new JdkHttpTransport(), new SoapRequestParser());

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/success", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            respond(exchange, soapEnvelope(
                    "<ns:GetCustomerResponse xmlns:ns=\"urn:example\"><name>Ada</name></ns:GetCustomerResponse>"));
        });
        server.createContext("/fault", exchange -> respond(exchange, soapEnvelope(
                "<soapenv:Fault><faultcode>soapenv:Server</faultcode><faultstring>no</faultstring></soapenv:Fault>")));
        server.start();
        port = server.getAddress().getPort();
    }

    private static String soapEnvelope(String bodyXml) {
        return "<soapenv:Envelope xmlns:soapenv=\"" + NS + "\"><soapenv:Body>" + bodyXml
                + "</soapenv:Body></soapenv:Envelope>";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private EndpointDefinition endpoint(String path, String soapAction) {
        return new EndpointDefinition("e", "http://127.0.0.1:" + port + path, EndpointType.SOAP, "POST",
                Map.of(), 1000, 2000, 0, soapAction);
    }

    private EsbExchange exchange() {
        EsbExchange exchange = new EsbExchange("trace", "r1", "POST", "/x", Map.of(), "127.0.0.1");
        exchange.request(EsbMessage.text("<GetCustomer><id>C-1</id></GetCustomer>"));
        return exchange;
    }

    @Test
    void wrapsPlainPayloadInEnvelope() {
        String wrapped = new String(client.wrapIfNeeded("<GetCustomer/>".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertTrue(wrapped.contains(":Envelope"));
        assertTrue(wrapped.contains(":Body"));
        assertTrue(wrapped.contains("<GetCustomer/>"));
    }

    @Test
    void leavesExistingEnvelopeUntouched() {
        byte[] envelope = soapEnvelope("<Ping/>").getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(envelope, client.wrapIfNeeded(envelope));
    }

    @Test
    void invokeWrapsBodySetsSoapActionAndExtractsResponseBody() {
        EsbMessage response = client.invoke(endpoint("/success", "urn:getCustomer"),
                EsbMessage.text("<GetCustomer><id>C-1</id></GetCustomer>"), exchange());

        assertEquals("text/xml", response.contentType());
        assertTrue(response.bodyAsString().contains("Ada"));
        assertTrue(lastRequestBody.get().contains(":Envelope"));
        assertEquals("\"urn:getCustomer\"", lastSoapAction.get());
    }

    @Test
    void soapFaultBecomesGatewayError() {
        EsbException error = assertThrows(EsbException.class,
                () -> client.invoke(endpoint("/fault", null), EsbMessage.text("<GetCustomer/>"), exchange()));
        assertEquals(EsbErrorCode.GATEWAY_ERROR, error.code());
    }
}
