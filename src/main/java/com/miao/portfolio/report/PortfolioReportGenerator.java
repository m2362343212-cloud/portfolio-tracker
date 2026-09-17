package com.miao.portfolio.report;

import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Formats a {@link Portfolio}'s current state into a human-readable text
 * report: one row per position plus a totals line, using supplied market
 * prices to value open positions.
 */
public final class PortfolioReportGenerator {

    private static final String ROW_FORMAT = "%-8s %10s %12s %12s %14s %14s%n";

    private PortfolioReportGenerator() {
    }

    public static String generate(Portfolio portfolio, Map<String, BigDecimal> marketPrices) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(78)).append(System.lineSeparator());
        sb.append("PORTFOLIO REPORT").append(System.lineSeparator());
        sb.append("=".repeat(78)).append(System.lineSeparator());
        sb.append(ROW_FORMAT.formatted("SYMBOL", "QTY", "AVG COST", "LAST", "MKT VALUE", "UNREALIZED"));
        sb.append("-".repeat(78)).append(System.lineSeparator());

        for (Position position : portfolio.getOpenPositions()) {
            BigDecimal price = marketPrices.get(position.getSymbol());
            if (price == null) {
                sb.append("%-8s (no market price supplied)%n".formatted(position.getSymbol()));
                continue;
            }
            sb.append(ROW_FORMAT.formatted(
                    position.getSymbol(),
                    money(position.getQuantity()),
                    money(position.getAverageCost()),
                    money(price),
                    money(position.marketValue(price)),
                    money(position.unrealizedPnl(price))));
        }

        sb.append("-".repeat(78)).append(System.lineSeparator());
        sb.append("Total realized P&L:   %s%n".formatted(money(portfolio.totalRealizedPnl())));
        sb.append("Total unrealized P&L: %s%n".formatted(money(portfolio.totalUnrealizedPnl(marketPrices))));
        sb.append("Total market value:   %s%n".formatted(money(portfolio.totalMarketValue(marketPrices))));
        sb.append("=".repeat(78)).append(System.lineSeparator());

        return sb.toString();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
