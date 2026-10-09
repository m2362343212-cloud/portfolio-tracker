package com.miao.portfolio.web;

import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Position;
import com.miao.portfolio.optimize.OptimizationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link Portfolio}'s current state as a single self-contained
 * HTML page: a summary of realized/unrealized P&amp;L and market value,
 * a table of open positions, a table of closed ones, and -- when the CLI
 * was run with {@code --optimize} and/or {@code --bayesian} -- an
 * Optimization section showing the same suggested weights (and, for
 * Black-Litterman, the same expected-return tables) that would otherwise
 * only be visible in the console output.
 *
 * <p>Kept separate from {@link Portfolio}/{@link Position} for the same
 * reason {@code PortfolioReportGenerator} is: the domain classes shouldn't
 * know or care how they're displayed. This class and the plain-text
 * generator are two interchangeable "views" over the same model.</p>
 */
public final class HtmlReportRenderer {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HtmlReportRenderer() {
    }

    /** One suggested-weights table for the Optimization section (mirrors {@code OptimizationReportGenerator}). */
    public record WeightTable(String title, OptimizationResult result) {
    }

    /**
     * One expected-return comparison table for the Optimization section -- used to show the
     * Black-Litterman prior (market-implied) returns next to the posterior (view-adjusted) ones.
     */
    public record ReturnsTable(String title, List<String> symbols, String beforeLabel, double[] beforeReturns,
                                String afterLabel, double[] afterReturns, String note) {
    }

    public static String render(Portfolio portfolio, Map<String, BigDecimal> marketPrices) {
        return render(portfolio, marketPrices, List.of(), List.of());
    }

    public static String render(Portfolio portfolio, Map<String, BigDecimal> marketPrices,
                                 List<ReturnsTable> returnsTables, List<WeightTable> weightTables) {
        List<Position> open = portfolio.getOpenPositions().stream()
                .sorted(Comparator.comparing(Position::getSymbol))
                .toList();
        List<Position> closed = portfolio.getAllPositions().stream()
                .filter(p -> !p.isOpen())
                .sorted(Comparator.comparing(Position::getSymbol))
                .toList();

        BigDecimal totalRealized = portfolio.totalRealizedPnl();
        BigDecimal totalUnrealized = safeTotalUnrealized(portfolio, marketPrices, open);
        BigDecimal totalMarketValue = safeTotalMarketValue(portfolio, marketPrices, open);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("<title>Portfolio Tracker</title>\n");
        html.append("<style>").append(CSS).append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<div class=\"page\">\n");
        html.append("<header><h1>Portfolio Tracker</h1>");
        html.append("<p class=\"timestamp\">Generated ").append(escape(LocalDateTime.now().format(TIMESTAMP_FORMAT)));
        html.append("</p></header>\n");

        html.append("<section class=\"stats\">\n");
        html.append(statCard("Total Realized P&amp;L", totalRealized));
        html.append(statCard("Total Unrealized P&amp;L", totalUnrealized));
        html.append(statCard("Total Market Value", totalMarketValue));
        html.append("</section>\n");

        html.append("<section>\n<h2>Open Positions</h2>\n");
        if (open.isEmpty()) {
            html.append("<p class=\"empty\">No open positions.</p>\n");
        } else {
            html.append("<table>\n<thead><tr>");
            html.append("<th>Symbol</th><th>Qty</th><th>Avg Cost</th><th>Last Price</th>");
            html.append("<th>Market Value</th><th>Unrealized P&amp;L</th>");
            html.append("</tr></thead>\n<tbody>\n");
            for (Position p : open) {
                BigDecimal price = marketPrices.get(p.getSymbol());
                html.append("<tr>");
                html.append("<td class=\"symbol\">").append(escape(p.getSymbol())).append("</td>");
                html.append("<td>").append(money(p.getQuantity())).append("</td>");
                html.append("<td>").append(money(p.getAverageCost())).append("</td>");
                if (price == null) {
                    html.append("<td colspan=\"3\" class=\"empty\">no market price supplied</td>");
                } else {
                    BigDecimal unrealized = p.unrealizedPnl(price);
                    html.append("<td>").append(money(price)).append("</td>");
                    html.append("<td>").append(money(p.marketValue(price))).append("</td>");
                    html.append("<td class=\"").append(pnlClass(unrealized)).append("\">")
                            .append(money(unrealized)).append("</td>");
                }
                html.append("</tr>\n");
            }
            html.append("</tbody>\n</table>\n");
        }
        html.append("</section>\n");

        html.append("<section>\n<h2>Closed Positions</h2>\n");
        if (closed.isEmpty()) {
            html.append("<p class=\"empty\">No closed positions.</p>\n");
        } else {
            html.append("<table>\n<thead><tr><th>Symbol</th><th>Trades</th><th>Realized P&amp;L</th></tr></thead>\n<tbody>\n");
            for (Position p : closed) {
                html.append("<tr>");
                html.append("<td class=\"symbol\">").append(escape(p.getSymbol())).append("</td>");
                html.append("<td>").append(p.getHistory().size()).append("</td>");
                html.append("<td class=\"").append(pnlClass(p.getRealizedPnl())).append("\">")
                        .append(money(p.getRealizedPnl())).append("</td>");
                html.append("</tr>\n");
            }
            html.append("</tbody>\n</table>\n");
        }
        html.append("</section>\n");

        if (!returnsTables.isEmpty() || !weightTables.isEmpty()) {
            html.append("<section>\n<h2>Optimization</h2>\n");
            for (ReturnsTable rt : returnsTables) {
                html.append(renderReturnsTable(rt));
            }
            for (WeightTable wt : weightTables) {
                html.append(renderWeightTable(wt));
            }
            html.append("</section>\n");
        }

        html.append("</div>\n</body>\n</html>\n");
        return html.toString();
    }

    private static String renderReturnsTable(ReturnsTable rt) {
        StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(rt.title())).append("</h3>\n");
        if (rt.note() != null && !rt.note().isBlank()) {
            html.append("<p class=\"empty\">").append(escape(rt.note())).append("</p>\n");
        }
        html.append("<table>\n<thead><tr><th>Symbol</th><th>")
                .append(escape(rt.beforeLabel())).append("</th><th>")
                .append(escape(rt.afterLabel())).append("</th></tr></thead>\n<tbody>\n");
        List<String> symbols = rt.symbols();
        for (int i = 0; i < symbols.size(); i++) {
            html.append("<tr>");
            html.append("<td class=\"symbol\">").append(escape(symbols.get(i))).append("</td>");
            html.append("<td>").append(percent(rt.beforeReturns()[i])).append("</td>");
            html.append("<td>").append(percent(rt.afterReturns()[i])).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
        return html.toString();
    }

    private static String renderWeightTable(WeightTable wt) {
        StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(wt.title())).append("</h3>\n");
        html.append("<table>\n<thead><tr><th>Symbol</th><th>Weight</th></tr></thead>\n<tbody>\n");
        for (Map.Entry<String, Double> entry : wt.result().getWeights().entrySet()) {
            html.append("<tr>");
            html.append("<td class=\"symbol\">").append(escape(entry.getKey())).append("</td>");
            html.append("<td>").append(percent(entry.getValue())).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
        html.append("<p class=\"empty\">Expected return: ").append(percent(wt.result().getExpectedReturn()))
                .append(" &middot; Volatility: ").append(percent(wt.result().getStandardDeviation()))
                .append(" &middot; converged after ").append(wt.result().getIterations()).append(" iterations</p>\n");
        return html.toString();
    }

    private static String percent(double value) {
        return "%.2f%%".formatted(value * 100);
    }

    private static BigDecimal safeTotalUnrealized(Portfolio portfolio, Map<String, BigDecimal> prices, List<Position> open) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position p : open) {
            BigDecimal price = prices.get(p.getSymbol());
            if (price != null) {
                total = total.add(p.unrealizedPnl(price));
            }
        }
        return total;
    }

    private static BigDecimal safeTotalMarketValue(Portfolio portfolio, Map<String, BigDecimal> prices, List<Position> open) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position p : open) {
            BigDecimal price = prices.get(p.getSymbol());
            if (price != null) {
                total = total.add(p.marketValue(price));
            }
        }
        return total;
    }

    private static String statCard(String label, BigDecimal value) {
        return "<div class=\"card\"><span class=\"card-label\">" + label + "</span>"
                + "<span class=\"card-value " + pnlClass(value) + "\">" + money(value) + "</span></div>\n";
    }

    private static String pnlClass(BigDecimal value) {
        return value.signum() < 0 ? "negative" : "positive";
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS = """
            :root { color-scheme: light dark; }
            body {
                margin: 0;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                background: #f5f6f8;
                color: #1a1a1a;
            }
            .page { max-width: 900px; margin: 0 auto; padding: 32px 24px 64px; }
            header h1 { margin: 0 0 4px; font-size: 28px; }
            .timestamp { margin: 0 0 24px; color: #666; font-size: 13px; }
            .stats { display: flex; gap: 16px; margin-bottom: 32px; flex-wrap: wrap; }
            .card {
                flex: 1;
                min-width: 200px;
                background: #fff;
                border: 1px solid #e2e2e2;
                border-radius: 10px;
                padding: 16px 20px;
                display: flex;
                flex-direction: column;
                gap: 6px;
                box-shadow: 0 1px 2px rgba(0,0,0,0.04);
            }
            .card-label { font-size: 13px; color: #666; }
            .card-value { font-size: 24px; font-weight: 600; }
            h2 { font-size: 18px; margin: 0 0 12px; }
            h3 { font-size: 14px; margin: 20px 0 8px; color: #444; }
            h3:first-of-type { margin-top: 0; }
            section { margin-bottom: 32px; }
            table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
            th, td { text-align: right; padding: 10px 14px; border-bottom: 1px solid #eee; font-variant-numeric: tabular-nums; }
            th:first-child, td:first-child { text-align: left; }
            th { background: #fafafa; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; color: #666; }
            td.symbol { font-weight: 600; }
            tr:last-child td { border-bottom: none; }
            .positive { color: #0a7d34; }
            .negative { color: #c0392b; }
            .empty { color: #888; font-size: 14px; }
            @media (prefers-color-scheme: dark) {
                body { background: #16171a; color: #e8e8e8; }
                .card, table { background: #1f2023; border-color: #2c2d30; box-shadow: none; }
                th { background: #232427; color: #9a9a9a; }
                th, td { border-bottom-color: #2c2d30; }
                .timestamp { color: #9a9a9a; }
                .positive { color: #3fbf6f; }
                .negative { color: #ff6b5e; }
                h3 { color: #b0b0b0; }
            }
            """;
}
