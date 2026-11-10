package com.king.esb.core.route;

/** The kinds of processors that may appear in a route pipeline. */
public enum ProcessorType {
    SET_HEADER,
    REMOVE_HEADER,
    LOG,
    FILTER,
    ROUTER,
    TRANSFORM,
    SERVICE_CALL,
    RATE_LIMIT
}
