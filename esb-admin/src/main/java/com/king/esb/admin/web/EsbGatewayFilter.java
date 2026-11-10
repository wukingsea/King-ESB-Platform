package com.king.esb.admin.web;

import com.king.esb.admin.web.dto.ErrorResponse;
import com.king.esb.common.EsbConstants;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.model.ExchangeStatus;
import com.king.esb.core.pipeline.PipelineContext;
import com.king.esb.core.pipeline.PipelineEngine;
import com.king.esb.core.registry.CompiledRoute;
import com.king.esb.core.registry.RouteRegistry;
import com.king.esb.core.route.RateLimitConfig;
import com.king.esb.core.route.RouteDefinition;
import com.king.esb.core.spi.RateLimiter;
import com.king.esb.governance.ExchangeNotifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound gateway. Matches the request against the runtime {@link RouteRegistry}, enforces API-key
 * and rate-limit rules, runs the route pipeline, and writes the response plus the
 * {@code X-ESB-Trace-Id} header. Admin, actuator, and console paths pass through untouched.
 */
@Component
public class EsbGatewayFilter extends OncePerRequestFilter {

    private final RouteRegistry registry;
    private final PipelineEngine pipelineEngine;
    private final PipelineContext pipelineContext;
    private final ExchangeNotifier notifier;
    private final ObjectProvider<RateLimiter> rateLimiterProvider;

    public EsbGatewayFilter(RouteRegistry registry, PipelineEngine pipelineEngine, PipelineContext pipelineContext,
                            ExchangeNotifier notifier, ObjectProvider<RateLimiter> rateLimiterProvider) {
        this.registry = registry;
        this.pipelineEngine = pipelineEngine;
        this.pipelineContext = pipelineContext;
        this.notifier = notifier;
        this.rateLimiterProvider = rateLimiterProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isReserved(path)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<CompiledRoute> match = registry.match(request.getMethod(), path);
        if (match.isEmpty()) {
            writeError(response, EsbErrorCode.ROUTE_NOT_FOUND, "No route matched the request", null);
            return;
        }
        RouteDefinition route = match.get().definition();
        String traceId = resolveTraceId(request);
        MDC.put("traceId", traceId);
        try {
            EsbExchange exchange = buildExchange(request, path, route, traceId);

            if (!apiKeyAllowed(route, request)) {
                exchange.status(ExchangeStatus.ERROR);
                exchange.httpStatus(EsbErrorCode.UNAUTHORIZED.httpStatus());
                exchange.error(new EsbException(EsbErrorCode.UNAUTHORIZED, "Invalid API key"));
                notifier.notify(exchange, exchange.httpStatus());
                writeError(response, EsbErrorCode.UNAUTHORIZED, "Invalid API key", traceId);
                return;
            }

            if (!rateLimitAllowed(route, exchange)) {
                exchange.status(ExchangeStatus.RATE_LIMITED);
                exchange.httpStatus(EsbErrorCode.RATE_LIMITED.httpStatus());
                exchange.error(new EsbException(EsbErrorCode.RATE_LIMITED, "Rate limit exceeded"));
                exchange.finish();
                notifier.notify(exchange, exchange.httpStatus());
                writeError(response, EsbErrorCode.RATE_LIMITED, "Rate limit exceeded", traceId);
                return;
            }

            pipelineEngine.execute(route, exchange, pipelineContext);
            exchange.finish();
            notifier.notify(exchange, exchange.httpStatus());
            writeResponse(response, exchange, traceId);
        } finally {
            MDC.remove("traceId");
        }
    }

    private EsbExchange buildExchange(HttpServletRequest request, String path, RouteDefinition route, String traceId) {
        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v.length > 0) {
                query.put(k, v[0]);
            }
        });
        EsbExchange exchange = new EsbExchange(traceId, route.id(), request.getMethod(), path, query, clientIp(request));
        exchange.request(toMessage(request));
        return exchange;
    }

    private EsbMessage toMessage(HttpServletRequest request) {
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new EsbException(EsbErrorCode.INTERNAL_ERROR, "Failed to read request body", e);
        }
        EsbMessage message = new EsbMessage(body, request.getContentType());
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!EsbConstants.HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    message.addHeader(name, values.nextElement());
                }
            }
        }
        return message;
    }

    private boolean apiKeyAllowed(RouteDefinition route, HttpServletRequest request) {
        String apiKey = route.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return true;
        }
        return apiKey.equals(request.getHeader(EsbConstants.API_KEY_HEADER));
    }

    private boolean rateLimitAllowed(RouteDefinition route, EsbExchange exchange) {
        RateLimitConfig config = route.rateLimit();
        RateLimiter limiter = rateLimiterProvider.getIfAvailable();
        if (config == null || !config.isActive() || limiter == null) {
            return true;
        }
        String key = "esb:rl:" + route.id() + ":" + partitionKey(config.keyBy(), exchange);
        return limiter.tryAcquire(key, config.requestsPerWindow(), config.windowSeconds());
    }

    private String partitionKey(String keyBy, EsbExchange exchange) {
        if (keyBy == null || "ip".equalsIgnoreCase(keyBy)) {
            return exchange.clientIp() == null ? "unknown" : exchange.clientIp();
        }
        if ("apikey".equalsIgnoreCase(keyBy)) {
            String value = exchange.request().header(EsbConstants.API_KEY_HEADER);
            return value == null ? "none" : value;
        }
        String value = exchange.request().header(keyBy);
        return value == null ? "none" : value;
    }

    private void writeResponse(HttpServletResponse response, EsbExchange exchange, String traceId)
            throws IOException {
        int status = exchange.httpStatus();
        response.setHeader(EsbConstants.TRACE_HEADER, traceId);
        response.setStatus(status);
        if (status == HttpServletResponse.SC_NO_CONTENT || exchange.status() != ExchangeStatus.OK) {
            if (exchange.status() == ExchangeStatus.OK) {
                return;
            }
            EsbException error = exchange.error() instanceof EsbException esb ? esb
                    : new EsbException(EsbErrorCode.INTERNAL_ERROR, "Exchange failed");
            writeError(response, error.code(), error.getMessage(), traceId);
            return;
        }
        EsbMessage responseMessage = exchange.response();
        if (responseMessage != null) {
            if (responseMessage.contentType() != null) {
                response.setContentType(responseMessage.contentType());
            }
            responseMessage.headers().forEach((name, values) -> {
                if (!EsbConstants.HOP_BY_HOP_HEADERS.contains(name.toLowerCase()) && !values.isEmpty()) {
                    response.setHeader(name, values.get(0));
                }
            });
            response.getOutputStream().write(responseMessage.body());
        }
    }

    private void writeError(HttpServletResponse response, EsbErrorCode code, String message, String traceId)
            throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (traceId != null) {
            response.setHeader(EsbConstants.TRACE_HEADER, traceId);
        }
        response.getWriter().write(JsonUtil.toJson(ErrorResponse.of(code, message, null, traceId)));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(EsbConstants.TRACE_HEADER);
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isReserved(String path) {
        return EsbConstants.RESERVED_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
