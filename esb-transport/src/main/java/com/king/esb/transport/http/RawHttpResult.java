package com.king.esb.transport.http;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/** Raw result of an HTTP exchange, decoupled from the JDK HTTP types. */
public record RawHttpResult(int status, byte[] body, Map<String, List<String>> headers) {

    public String contentType() {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if ("content-type".equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    static RawHttpResult from(HttpResponse<byte[]> response) {
        return new RawHttpResult(response.statusCode(), response.body(), response.headers().map());
    }
}
