package com.miao.portfolio.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks the running holding in a single instrument using the
 * average-cost-basis method: every BUY blends into a single weighted-average
 * cost, and every SELL realizes P&amp;L against that average without
 * disturbing it.
 *
 * <p>This is the same method most retail brokerages use for tax reporting
 * (as opposed to FIFO/LIFO lot matching), and it's simpler to reason about
 * and test, which is why it's the right default for a first version.</p>
 */
public class Position {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final String symbol;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal averageCost = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private final List<Trade> history = new ArrayList<>();

    public Position(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Applies a trade to this position, updating quantity, average cost,
     * and realized P&amp;L as appropriate.
     *
     * @throws InsufficientPositionException if a SELL would take quantity below zero
     */
    public void apply(Trade trade) {
        if (!trade.getSymbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "Trade for %s cannot be applied to position %s".formatted(trade.getSymbol(), symbol));
        }

        switch (trade.getSide()) {
            case BUY -> applyBuy(trade);
            case SELL -> applySell(trade);
        }
        history.add(trade);
    }

    private void applyBuy(Trade trade) {
        BigDecimal newQuantity = quantity.add(trade.getQuantity());
        // Weighted average: (oldQty * oldAvgCost + buyQty * buyPrice) / newQty
        BigDecimal existingCostBasis = quantity.multiply(averageCost);
        BigDecimal incomingCostBasis = trade.getQuantity().multiply(trade.getPrice());
        this.averageCost = existingCostBasis.add(incomingCostBasis).divide(newQuantity, MC);
        this.quantity = newQuantity;
    }

    private void applySell(Trade trade) {
        if (trade.getQuantity().compareTo(quantity) > 0) {
            throw new InsufficientPositionException(symbol, quantity, trade.getQuantity());
        }
        // Realized P&L on this lot = quantity sold * (sale price - average cost).
        // Average cost is unchanged by a sell — only realized P&L and quantity move.
        BigDecimal gainPerShare = trade.getPrice().subtract(averageCost);
        BigDecimal realizedOnThisTrade = gainPerShare.multiply(trade.getQuantity());
        this.realizedPnl = this.realizedPnl.add(realizedOnThisTrade);
        this.quantity = quantity.subtract(trade.getQuantity());

        // Fully closed positions reset their cost basis; there is nothing left to be "average" of.
        if (this.quantity.signum() == 0) {
            this.averageCost = BigDecimal.ZERO;
        }
    }

    public BigDecimal unrealizedPnl(BigDecimal currentMarketPrice) {
        if (quantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return currentMarketPrice.subtract(averageCost).multiply(quantity);
    }

    public BigDecimal marketValue(BigDecimal currentMarketPrice) {
        return currentMarketPrice.multiply(quantity);
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public boolean isOpen() {
        return quantity.signum() > 0;
    }

    public List<Trade> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
