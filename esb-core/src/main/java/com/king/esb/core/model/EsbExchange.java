package com.king.esb.core.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The unit of work flowing through the ESB: request message, response message, routing metadata,
 * per-exchange variables and timing/error state.
 */
public final class EsbExchange {

    private final String traceId;
    private final String routeId;
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final String clientIp;
    private final Instant startTime = Instant.now();
    private final Map<String, Object> variables = new HashMap<>();

    private EsbMessage request = EsbMessage.empty();
    private EsbMessage response;
    private ExchangeStatus status = ExchangeStatus.OK;
    private Throwable error;
    private int httpStatus = 200;
    private long durationMillis = -1;

    public EsbExchange(String traceId, String routeId, String method, String path,
                       Map<String, String> query, String clientIp) {
        this.traceId = traceId;
        this.routeId = routeId;
        this.method = method;
        this.path = path;
        this.query = query == null ? Map.of() : Map.copyOf(query);
        this.clientIp = clientIp;
    }

    public String traceId() {
        return traceId;
    }

    public String routeId() {
        return routeId;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> query() {
        return query;
    }

    public String queryParam(String name) {
        return query.get(name);
    }

    public String clientIp() {
        return clientIp;
    }

    public Instant startTime() {
        return startTime;
    }

    public EsbMessage request() {
        return request;
    }

    public void request(EsbMessage request) {
        this.request = request;
    }

    public EsbMessage response() {
        return response;
    }

    public void response(EsbMessage response) {
        this.response = response;
    }

    public ExchangeStatus status() {
        return status;
    }

    public void status(ExchangeStatus status) {
        this.status = status;
    }

    public Throwable error() {
        return error;
    }

    public void error(Throwable error) {
        this.error = error;
        if (error != null) {
            this.status = ExchangeStatus.ERROR;
        }
    }

    public int httpStatus() {
        return httpStatus;
    }

    public void httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public void finish() {
        this.durationMillis = java.time.Duration.between(startTime, Instant.now()).toMillis();
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object variable(String name) {
        return variables.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T variable(String name, Class<T> type) {
        return (T) variables.get(name);
    }

    public Map<String, Object> variables() {
        return Map.copyOf(variables);
    }

    @Override
    public String toString() {
        return "EsbExchange{traceId=" + traceId + ", routeId=" + routeId + ", method=" + method
                + ", path=" + path + ", status=" + status + ", httpStatus=" + httpStatus + '}';
    }
}
