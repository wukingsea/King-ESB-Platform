package com.king.esb.transport.soap;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.core.spi.OutboundClient;
import com.king.esb.transport.http.JdkHttpTransport;
import com.king.esb.transport.http.RawHttpResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SOAP 1.1 outbound client. Wraps the exchange body in a SOAP envelope (unless it is already one),
 * sets the {@code SOAPAction} header, then extracts the {@code Body} payload from the response.
 * A SOAP {@code Fault} is surfaced as a {@link EsbErrorCode#GATEWAY_ERROR} carrying the fault code.
 */
@Component
public class SoapOutboundClient implements OutboundClient {

    static final String SOAP_1_1_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String SOAP_CONTENT_TYPE = "text/xml; charset=utf-8";

    private static final Set<String> RESTRICTED_HEADERS =
            Set.of("connection", "content-length", "expect", "host", "upgrade");

    private final JdkHttpTransport transport;
    private final SoapRequestParser parser;

    public SoapOutboundClient(JdkHttpTransport transport, SoapRequestParser parser) {
        this.transport = transport;
        this.parser = parser;
    }

    @Override
    public EndpointType supports() {
        return EndpointType.SOAP;
    }

    @Override
    public EsbMessage invoke(EndpointDefinition endpoint, EsbMessage request, EsbExchange exchange) {
        Map<String, String> headers = buildHeaders(endpoint, request);
        byte[] body = wrapIfNeeded(request.body());
        RawHttpResult result = transport.send("POST", URI.create(endpoint.uri()), headers, body,
                endpoint.connectTimeoutMs(), endpoint.readTimeoutMs());

        if (result.status() >= 500) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR,
                    "SOAP upstream returned " + result.status() + " from " + endpoint.uri());
        }
        exchange.httpStatus(result.status());

        EsbMessage raw = new EsbMessage(result.body(), "text/xml");
        SoapRequestParser.SoapParts parts = parser.parse(raw);
        if (parts.fault()) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR,
                    "SOAP Fault from " + endpoint.uri() + ": "
                            + (parts.faultCode() == null ? "unknown" : parts.faultCode()));
        }
        EsbMessage response = new EsbMessage(parts.bodyXml().getBytes(StandardCharsets.UTF_8), "text/xml");
        return response;
    }

    /** Wraps the payload in a SOAP 1.1 envelope unless it already is one. */
    byte[] wrapIfNeeded(byte[] payload) {
        String xml = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
        if (isEnvelope(xml)) {
            return payload;
        }
        return wrap(xml).getBytes(StandardCharsets.UTF_8);
    }

    /** Builds a SOAP 1.1 envelope around the given body XML. */
    String wrap(String bodyXml) {
        return "<soapenv:Envelope xmlns:soapenv=\"" + SOAP_1_1_ENVELOPE_NS + "\">"
                + "<soapenv:Body>" + (bodyXml == null ? "" : bodyXml) + "</soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    private boolean isEnvelope(String xml) {
        String trimmed = xml.stripLeading();
        return trimmed.contains(":Envelope") || trimmed.contains("<Envelope");
    }

    private Map<String, String> buildHeaders(EndpointDefinition endpoint, EsbMessage request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach((name, values) -> {
            if (isSafe(name) && !values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        endpoint.headers().forEach((name, value) -> {
            if (isSafe(name)) {
                headers.put(name, value);
            }
        });
        headers.put("Content-Type", SOAP_CONTENT_TYPE);
        String soapAction = endpoint.soapAction() != null ? endpoint.soapAction() : request.header("SOAPAction");
        if (soapAction != null) {
            headers.put("SOAPAction", "\"" + soapAction.replace("\"", "") + "\"");
        }
        return headers;
    }

    private boolean isSafe(String name) {
        return name != null && !RESTRICTED_HEADERS.contains(name.toLowerCase());
    }
}
