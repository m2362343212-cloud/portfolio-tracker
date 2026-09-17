package com.miao.portfolio.model;

/**
 * Which side of the market a trade was executed on.
 */
public enum TradeSide {
    BUY,
    SELL;

    /**
     * Parses a side from CSV input, tolerant of case ("buy", "Buy", "BUY").
     */
    public static TradeSide fromString(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Trade side cannot be null");
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "BUY", "B" -> BUY;
            case "SELL", "S" -> SELL;
            default -> throw new IllegalArgumentException("Unrecognized trade side: " + raw);
        };
    }
}
