package com.miao.portfolio.web;

import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Position;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Turns a Portfolio into JSON for the browser. Money values are sent as
 * strings (e.g. "123.45"), not JSON numbers, so the exact BigDecimal value
 * reaches the page without being rounded by JavaScript's floating point.
 */
public final class JsonRenderer {

    private JsonRenderer() {
    }

    public static String render(Portfolio portfolio, Map<String, BigDecimal> prices) {
        List<Position> open = portfolio.getOpenPositions().stream()
                .sorted(Comparator.comparing(Position::getSymbol)).toList();
        List<Position> closed = portfolio.getAllPositions().stream()
                .filter(p -> !p.isOpen())
                .sorted(Comparator.comparing(Position::getSymbol)).toList();

        StringJoiner openJson = new StringJoiner(",", "[", "]");
        for (Position p : open) {
            BigDecimal price = prices.get(p.getSymbol());
            openJson.add("{"
                    + field("symbol", p.getSymbol()) + ","
                    + field("quantity", p.getQuantity()) + ","
                    + field("averageCost", p.getAverageCost()) + ","
                    + field("marketPrice", price) + ","
                    + field("marketValue", price == null ? null : p.marketValue(price)) + ","
                    + field("unrealizedPnl", price == null ? null : p.unrealizedPnl(price))
                    + "}");
        }
        StringJoiner closedJson = new StringJoiner(",", "[", "]");
        for (Position p : closed) {
            closedJson.add("{" + field("symbol", p.getSymbol()) + ","
                    + field("realizedPnl", p.getRealizedPnl()) + "}");
        }
        return "{\"totals\":{"
                + field("realizedPnl", portfolio.totalRealizedPnl()) + ","
                + field("unrealizedPnl", portfolio.totalUnrealizedPnl(prices)) + ","
                + field("marketValue", portfolio.totalMarketValue(prices))
                + "},\"open\":" + openJson + ",\"closed\":" + closedJson + "}";
    }

    /** {"error":"..."} for 4xx responses. */
    public static String error(String message) {
        return "{" + field("error", message) + "}";
    }

    private static String field(String name, Object value) {
        String v = value == null ? "null"
                : "\"" + escape(value instanceof BigDecimal b ? b.toPlainString() : value.toString()) + "\"";
        return "\"" + name + "\":" + v;
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
