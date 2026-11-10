package com.king.esb.core.route;

/** Behaviour when a pipeline step fails. */
public enum ErrorStrategy {
    /** Fail the whole exchange and surface a gateway error. */
    DEFAULT,
    /** Ignore the step failure and keep the current (last good) response. */
    CONTINUE
}
