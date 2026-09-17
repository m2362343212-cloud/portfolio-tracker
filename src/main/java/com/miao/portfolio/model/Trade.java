package com.miao.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An immutable record of a single trade execution.
 *
 * <p>Trades are treated as value objects: two trades with identical fields
 * are considered equal, and instances can never be mutated after
 * construction. This mirrors how a real trade blotter should behave — a
 * booked trade is a fact, not something you edit in place.</p>
 */
public final class Trade {

    private final LocalDate date;
    private final String symbol;
    private final TradeSide side;
    private final BigDecimal quantity;
    private final BigDecimal price;

    public Trade(LocalDate date, String symbol, TradeSide side, BigDecimal quantity, BigDecimal price) {
        this.date = Objects.requireNonNull(date, "date");
        this.symbol = normalizeSymbol(symbol);
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = requirePositive(quantity, "quantity");
        this.price = requirePositive(price, "price");
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol cannot be blank");
        }
        return symbol.trim().toUpperCase();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, was " + value);
        }
        return value;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /** Gross cash impact of this trade, always positive (sign handled by the caller). */
    public BigDecimal grossAmount() {
        return price.multiply(quantity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade trade)) return false;
        return date.equals(trade.date)
                && symbol.equals(trade.symbol)
                && side == trade.side
                && quantity.compareTo(trade.quantity) == 0
                && price.compareTo(trade.price) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, symbol, side, quantity.stripTrailingZeros(), price.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "Trade{%s %s %s %s @ %s}".formatted(date, side, quantity, symbol, price);
    }
}
