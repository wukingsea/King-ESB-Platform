package com.king.esb.app.internal;

import com.king.esb.common.EsbConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Platform-internal echo endpoint used by the dev demo route. It is served under
 * {@link EsbConstants#INTERNAL_PREFIX}, a reserved prefix that the gateway filter passes through
 * untouched, so a route may safely target this application itself.
 */
@RestController
public class InternalEchoController {

    @RequestMapping(value = "/internal/echo",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Map<String, Object>> echo(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", request.getMethod());
        result.put("path", request.getRequestURI());
        result.put("query", request.getQueryString());
        result.put("contentType", request.getContentType());
        result.put("body", new String(body, StandardCharsets.UTF_8));
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                headers.put(name, request.getHeader(name));
            }
        }
        result.put("headers", headers);
        return ResponseEntity.ok(result);
    }
}
