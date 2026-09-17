package com.miao.portfolio.model;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates every {@link Position} in the book, keyed by symbol.
 *
 * <p>{@code Portfolio} is the entry point for feeding trades in and pulling
 * summary numbers back out: it owns the map of one {@link Position} per
 * instrument and knows how to route a trade to the right one, creating it
 * on first sight.</p>
 */
public class Portfolio {

    // LinkedHashMap so iteration order (and therefore report order) matches
    // the order symbols were first traded, which is easier for a human to
    // read than an arbitrary hash order.
    private final Map<String, Position> positions = new LinkedHashMap<>();

    /**
     * Applies a single trade, creating a new {@link Position} for its
     * symbol the first time that symbol is seen.
     */
    public void applyTrade(Trade trade) {
        Position position = positions.computeIfAbsent(trade.getSymbol(), Position::new);
        position.apply(trade);
    }

    /**
     * Convenience for loading a whole batch of trades at once (e.g. from a
     * CSV file), applied in the given order.
     */
    public void applyTrades(Collection<Trade> trades) {
        for (Trade trade : trades) {
            applyTrade(trade);
        }
    }

    /** The position for a symbol, or {@code null} if that symbol has never been traded. */
    public Position getPosition(String symbol) {
        return positions.get(symbol.trim().toUpperCase());
    }

    /** All positions ever opened, including ones that have since been fully closed. */
    public Collection<Position> getAllPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    /** Only positions with a nonzero quantity right now. */
    public Collection<Position> getOpenPositions() {
        return positions.values().stream()
                .filter(Position::isOpen)
                .toList();
    }

    /** Sum of realized P&amp;L across every position, open or closed. */
    public BigDecimal totalRealizedPnl() {
        return positions.values().stream()
                .map(Position::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Sum of unrealized P&amp;L across all open positions, given a map of
     * current market prices keyed by symbol.
     *
     * @throws IllegalStateException if an open position's symbol is missing from {@code marketPrices}
     */
    public BigDecimal totalUnrealizedPnl(Map<String, BigDecimal> marketPrices) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position position : getOpenPositions()) {
            BigDecimal price = requirePrice(marketPrices, position.getSymbol());
            total = total.add(position.unrealizedPnl(price));
        }
        return total;
    }

    /** Sum of market value across all open positions, given current market prices. */
    public BigDecimal totalMarketValue(Map<String, BigDecimal> marketPrices) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position position : getOpenPositions()) {
            BigDecimal price = requirePrice(marketPrices, position.getSymbol());
            total = total.add(position.marketValue(price));
        }
        return total;
    }

    private static BigDecimal requirePrice(Map<String, BigDecimal> marketPrices, String symbol) {
        BigDecimal price = marketPrices.get(symbol);
        if (price == null) {
            throw new IllegalStateException("No market price provided for open position: " + symbol);
        }
        return price;
    }
}
