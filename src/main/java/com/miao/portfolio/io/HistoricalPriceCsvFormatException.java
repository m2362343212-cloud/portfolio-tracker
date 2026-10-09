package com.miao.portfolio.io;

/**
 * Thrown when a line of a historical-price CSV can't be parsed — wrong
 * column count, or an unparseable date/price.
 *
 * <p>Reports the 1-based line number so a bad file can be fixed quickly
 * instead of leaving the user to guess which row is malformed.</p>
 */
public class HistoricalPriceCsvFormatException extends RuntimeException {

    public HistoricalPriceCsvFormatException(int lineNumber, String reason) {
        super("Malformed historical-price CSV at line %d: %s".formatted(lineNumber, reason));
    }

    public HistoricalPriceCsvFormatException(int lineNumber, String reason, Throwable cause) {
        super("Malformed historical-price CSV at line %d: %s".formatted(lineNumber, reason), cause);
    }
}
