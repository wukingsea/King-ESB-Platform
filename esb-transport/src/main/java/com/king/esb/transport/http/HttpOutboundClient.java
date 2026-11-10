package com.king.esb.transport.http;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.core.spi.OutboundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP/REST outbound client. Retries on {@code 5xx} responses and transport errors with exponential
 * backoff, and maps the backend status/headers/body into an {@link EsbMessage}.
 */
@Component
public class HttpOutboundClient implements OutboundClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOutboundClient.class);
    private static final long BACKOFF_BASE_MS = 200L;

    /** Headers the JDK HttpClient forbids callers from setting. */
    static final Set<String> RESTRICTED_HEADERS =
            Set.of("connection", "content-length", "expect", "host", "upgrade");

    private final JdkHttpTransport transport;

    public HttpOutboundClient(JdkHttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public EndpointType supports() {
        return EndpointType.HTTP;
    }

    @Override
    public EsbMessage invoke(EndpointDefinition endpoint, EsbMessage request, EsbExchange exchange) {
        int maxAttempts = endpoint.retryCount() + 1;
        EsbException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return doInvoke(endpoint, request, exchange);
            } catch (EsbException e) {
                last = e;
                if (attempt < maxAttempts) {
                    log.debug("Retrying upstream call attempt {}/{} for {}: {}",
                            attempt, maxAttempts, endpoint.uri(), e.getMessage());
                    backoff(attempt);
                }
            }
        }
        throw last;
    }

    private EsbMessage doInvoke(EndpointDefinition endpoint, EsbMessage request, EsbExchange exchange) {
        Map<String, String> headers = buildHeaders(endpoint, request);
        byte[] body = request.body();
        RawHttpResult result = transport.send(endpoint.method(), URI.create(endpoint.uri()), headers, body,
                endpoint.connectTimeoutMs(), endpoint.readTimeoutMs());

        if (result.status() >= 500) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR,
                    "Upstream returned " + result.status() + " from " + endpoint.uri());
        }
        exchange.httpStatus(result.status());
        EsbMessage response = new EsbMessage(result.body(), result.contentType());
        result.headers().forEach((name, values) -> {
            if (isSafeResponseHeader(name)) {
                values.forEach(v -> response.addHeader(name, v));
            }
        });
        return response;
    }

    private Map<String, String> buildHeaders(EndpointDefinition endpoint, EsbMessage request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach((name, values) -> {
            if (isSafeHeader(name) && !values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        endpoint.headers().forEach((name, value) -> {
            if (isSafeHeader(name)) {
                headers.put(name, value);
            }
        });
        if (request.contentType() != null && isSafeHeader("content-type")) {
            headers.put("Content-Type", request.contentType());
        }
        return headers;
    }

    private boolean isSafeHeader(String name) {
        return name != null && !RESTRICTED_HEADERS.contains(name.toLowerCase());
    }

    private boolean isSafeResponseHeader(String name) {
        return name != null
                && !Set.of("transfer-encoding", "content-length", "connection").contains(name.toLowerCase());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Exposed for tests. */
    public List<String> restrictedHeaderNames() {
        return List.copyOf(RESTRICTED_HEADERS);
    }
}
