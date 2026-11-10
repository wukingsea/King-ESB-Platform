package com.king.esb.transport.http;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper around the JDK {@link HttpClient} providing per-endpoint timeouts. Clients are cached
 * per connect-timeout so repeated calls reuse pooled connections.
 */
@Component
public class JdkHttpTransport {

    private final Map<Integer, HttpClient> clients = new ConcurrentHashMap<>();

    public RawHttpResult send(String method, URI uri, Map<String, String> headers, byte[] body,
                              int connectTimeoutMs, int readTimeoutMs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(readTimeoutMs));
        headers.forEach(builder::header);
        HttpRequest.BodyPublisher publisher = (body == null || body.length == 0)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        builder.method(method, publisher);
        try {
            HttpResponse<byte[]> response = client(connectTimeoutMs).send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return RawHttpResult.from(response);
        } catch (IOException e) {
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "Upstream call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EsbException(EsbErrorCode.GATEWAY_ERROR, "Upstream call interrupted", e);
        }
    }

    private HttpClient client(int connectTimeoutMs) {
        return clients.computeIfAbsent(connectTimeoutMs, timeout -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }
}
