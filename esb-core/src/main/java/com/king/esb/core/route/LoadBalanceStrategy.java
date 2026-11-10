package com.king.esb.core.route;

/** How an endpoint is selected from a target's endpoint pool. */
public enum LoadBalanceStrategy {
    ROUND_ROBIN,
    RANDOM,
    FAILOVER
}
