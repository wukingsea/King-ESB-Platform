package com.king.esb.transport.soap;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapRequestParserTest {

    private static final String NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private final SoapRequestParser parser = new SoapRequestParser();

    private EsbMessage envelope(String bodyXml) {
        String xml = "<soapenv:Envelope xmlns:soapenv=\"" + NS + "\"><soapenv:Body>" + bodyXml
                + "</soapenv:Body></soapenv:Envelope>";
        return new EsbMessage(xml.getBytes(StandardCharsets.UTF_8), "text/xml");
    }

    @Test
    void detectsSoapByContentType() {
        assertTrue(parser.isSoap(new EsbMessage(new byte[0], "text/xml; charset=utf-8")));
        assertTrue(parser.isSoap(new EsbMessage(new byte[0], "application/soap+xml")));
        assertFalse(parser.isSoap(new EsbMessage(new byte[0], "application/json")));
        assertFalse(parser.isSoap(new EsbMessage(new byte[0], null)));
    }

    @Test
    void extractsBodyPayload() {
        SoapRequestParser.SoapParts parts = parser.parse(envelope("<GetCustomer><id>C-1</id></GetCustomer>"));

        assertFalse(parts.fault());
        assertTrue(parts.bodyXml().contains("<GetCustomer>"));
        assertTrue(parts.bodyXml().contains("<id>C-1</id>"));
    }

    @Test
    void extractBodyHelperDelegatesToParse() {
        String body = parser.extractBody(envelope("<Ping/>"));
        assertTrue(body.contains("<Ping/>"));
    }

    @Test
    void readsSoapActionHeaderWithoutQuotes() {
        EsbMessage message = envelope("<Ping/>");
        message.setHeader("SOAPAction", "\"urn:ping\"");
        assertEquals("urn:ping", parser.parse(message).soapAction());
    }

    @Test
    void detectsFaultAndFaultCode() {
        SoapRequestParser.SoapParts parts = parser.parse(envelope(
                "<soapenv:Fault><faultcode>soapenv:Server</faultcode>"
                        + "<faultstring>boom</faultstring></soapenv:Fault>"));

        assertTrue(parts.fault());
        assertEquals("soapenv:Server", parts.faultCode());
    }

    @Test
    void envelopeWithoutBodyFails() {
        String xml = "<soapenv:Envelope xmlns:soapenv=\"" + NS + "\"></soapenv:Envelope>";
        EsbException error = assertThrows(EsbException.class,
                () -> parser.parse(new EsbMessage(xml.getBytes(StandardCharsets.UTF_8), "text/xml")));
        assertEquals(EsbErrorCode.GATEWAY_ERROR, error.code());
    }

    @Test
    void invalidXmlFails() {
        EsbException error = assertThrows(EsbException.class,
                () -> parser.parse(new EsbMessage("<not-xml".getBytes(StandardCharsets.UTF_8), "text/xml")));
        assertEquals(EsbErrorCode.GATEWAY_ERROR, error.code());
    }
}
