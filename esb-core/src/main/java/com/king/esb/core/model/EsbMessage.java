package com.king.esb.core.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mutable message payload carried through a pipeline: raw bytes plus case-insensitive headers
 * and a content type. Immutable copies are produced via {@link #copy()} when needed.
 */
public final class EsbMessage {

    private byte[] body;
    private String contentType;
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public EsbMessage() {
        this.body = new byte[0];
    }

    public EsbMessage(byte[] body, String contentType) {
        this.body = body == null ? new byte[0] : body;
        this.contentType = contentType;
    }

    public static EsbMessage empty() {
        return new EsbMessage();
    }

    public static EsbMessage text(String text) {
        return new EsbMessage(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    public static EsbMessage of(byte[] body, String contentType) {
        return new EsbMessage(body, contentType);
    }

    public byte[] body() {
        return body;
    }

    public void body(byte[] body) {
        this.body = body == null ? new byte[0] : body;
    }

    public void body(String body) {
        this.body = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean isEmpty() {
        return body.length == 0;
    }

    public String contentType() {
        return contentType;
    }

    public void contentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, List<String>> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public String header(String name) {
        List<String> values = headers.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    public List<String> headerValues(String name) {
        return headers.getOrDefault(name, List.of());
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }

    public void setHeader(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name, values);
    }

    public void addHeader(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    public void removeHeader(String name) {
        headers.remove(name);
    }

    /** Deep-enough copy used when a processor must not mutate the incoming message in place. */
    public EsbMessage copy() {
        EsbMessage copy = new EsbMessage(this.body.clone(), this.contentType);
        this.headers.forEach((k, v) -> copy.headers.put(k, new ArrayList<>(v)));
        return copy;
    }

    @Override
    public String toString() {
        return "EsbMessage{bodyLength=" + body.length + ", contentType=" + contentType
                + ", headers=" + headers.keySet() + '}';
    }
}
