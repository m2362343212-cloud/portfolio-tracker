package com.miao.portfolio.io;

/**
 * Thrown when a line of the trade CSV can't be parsed — wrong column
 * count, an unparseable date/number, or an unrecognized trade side.
 *
 * <p>Reports the 1-based line number so a bad file can be fixed quickly
 * instead of leaving the user to guess which row is malformed.</p>
 */
public class TradeCsvFormatException extends RuntimeException {

    public TradeCsvFormatException(int lineNumber, String reason) {
        super("Malformed trade CSV at line %d: %s".formatted(lineNumber, reason));
    }

    public TradeCsvFormatException(int lineNumber, String reason, Throwable cause) {
        super("Malformed trade CSV at line %d: %s".formatted(lineNumber, reason), cause);
    }
}
