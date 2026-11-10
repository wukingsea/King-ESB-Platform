package com.king.esb.transport.soap;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

/**
 * Parses SOAP 1.1 envelopes: detects SOAP by content type, exposes the {@code Body} payload and the
 * {@code SOAPAction} header, and recognises {@code Fault} responses.
 */
@Component
public class SoapRequestParser {

    /** Result of parsing a SOAP envelope. */
    public record SoapParts(String bodyXml, String soapAction, boolean fault, String faultCode) {
    }

    public boolean isSoap(EsbMessage message) {
        String contentType = message.contentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.contains("text/xml") || lower.contains("application/soap+xml");
    }

    public SoapParts parse(EsbMessage message) {
        Document document = parseDocument(message.body());
        Element envelope = document.getDocumentElement();
        Element body = firstChildByLocalName(envelope, "Body");
        if (body == null) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "SOAP envelope has no Body element");
        }
        Element payload = firstElementChild(body);
        String bodyXml = payload == null ? "" : serialize(payload);
        boolean fault = payload != null && "Fault".equals(payload.getLocalName());
        String faultCode = fault ? textOf(firstChildByLocalName(payload, "faultcode")) : null;
        String soapAction = headerValue(message, "SOAPAction");
        return new SoapParts(bodyXml, soapAction, fault, faultCode);
    }

    /** Extracts the SOAP body payload from a raw SOAP response message. */
    public String extractBody(EsbMessage message) {
        return parse(message).bodyXml();
    }

    private String headerValue(EsbMessage message, String name) {
        String value = message.header(name);
        return value == null ? null : value.replace("\"", "");
    }

    private Document parseDocument(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "Invalid SOAP/XML payload: " + e.getMessage(), e);
        }
    }

    private Element firstChildByLocalName(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private Element firstElementChild(Element parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    private String textOf(Element element) {
        return element == null ? null : element.getTextContent();
    }

    private String serialize(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "Failed to serialize SOAP body: " + e.getMessage(), e);
        }
    }
}
