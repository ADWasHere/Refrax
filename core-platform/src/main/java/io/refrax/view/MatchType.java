package io.refrax.view;

/**
 * How a query axis matches values. The set is closed and compiler-checked so a misspelt
 * match kind cannot be declared.
 */
public enum MatchType {

    /** Exact equality on a single value. */
    EXACT,

    /** A range (currently used for the valid-time axis). */
    RANGE
}
