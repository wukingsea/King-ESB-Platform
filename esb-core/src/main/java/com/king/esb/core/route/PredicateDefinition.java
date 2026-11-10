package com.king.esb.core.route;

/**
 * A single content-based condition. {@code source} follows the expression syntax understood by the
 * engine, e.g. {@code header:X-Tenant}, {@code path}, {@code method}, {@code query:env},
 * {@code body:customer.tier}.
 */
public record PredicateDefinition(String source, PredicateOperator operator, String value) {

    public PredicateDefinition {
        operator = operator == null ? PredicateOperator.EXISTS : operator;
    }
}
