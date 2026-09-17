package com.miao.portfolio.model;

import java.math.BigDecimal;

/**
 * Thrown when a SELL trade would take a position below zero.
 *
 * <p>This tracker is intentionally long-only for simplicity: it models a
 * portfolio that only sells what it already holds, rather than supporting
 * short selling. That's a deliberate scope decision, not an oversight —
 * see the README for how short positions could be added.</p>
 */
public class InsufficientPositionException extends RuntimeException {

    public InsufficientPositionException(String symbol, BigDecimal held, BigDecimal requested) {
        super("Cannot sell %s of %s: only %s held".formatted(requested, symbol, held));
    }
}
