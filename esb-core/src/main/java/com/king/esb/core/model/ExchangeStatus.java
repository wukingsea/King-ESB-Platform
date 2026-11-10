package com.king.esb.core.model;

/** Terminal status of an exchange after its pipeline has run. */
public enum ExchangeStatus {
    OK,
    ERROR,
    FILTERED,
    RATE_LIMITED;

    public boolean isSuccess() {
        return this == OK;
    }
}
