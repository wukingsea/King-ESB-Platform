package com.king.esb.common;

import java.util.Set;

/** Shared constants: header names, reserved paths and hop-by-hop headers. */
public final class EsbConstants {

    private EsbConstants() {
    }

    /** Response/diagnostic header carrying the exchange trace id. */
    public static final String TRACE_HEADER = "X-ESB-Trace-Id";

    /** Optional per-route API key header for gateway access. */
    public static final String API_KEY_HEADER = "X-ESB-Api-Key";

    /** Prefix for all admin API endpoints (excluded from gateway matching). */
    public static final String ADMIN_PREFIX = "/admin";

    /** Prefix for actuator endpoints (excluded from gateway matching). */
    public static final String ACTUATOR_PREFIX = "/actuator";

    /** Prefix for the static admin console (excluded from gateway matching). */
    public static final String CONSOLE_PREFIX = "/console";

    /** Prefix for platform-internal endpoints (demo/echo, diagnostics) excluded from gateway matching. */
    public static final String INTERNAL_PREFIX = "/internal";

    /** Path prefixes that must never be claimed by a user-defined route. */
    public static final Set<String> RESERVED_PREFIXES =
            Set.of(ADMIN_PREFIX, ACTUATOR_PREFIX, CONSOLE_PREFIX, INTERNAL_PREFIX);

    /** Headers that must not be propagated across a proxy hop. */
    public static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "content-length", "transfer-encoding",
            "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "upgrade");

    public static final String DEFAULT_TRANSFORM_CONTENT_TYPE = "application/json";
    public static final String CONTENT_TYPE_XML = "application/xml";
    public static final String CONTENT_TYPE_SOAP = "text/xml";
    public static final String CONTENT_TYPE_SOAP12 = "application/soap+xml";
}
