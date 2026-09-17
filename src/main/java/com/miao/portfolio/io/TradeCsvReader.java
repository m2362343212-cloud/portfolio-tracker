package com.miao.portfolio.io;

import com.miao.portfolio.model.Trade;
import com.miao.portfolio.model.TradeSide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a trade blotter from a simple CSV format:
 *
 * <pre>
 * date,symbol,side,quantity,price
 * 2026-01-05,AAPL,BUY,10,185.20
 * 2026-01-12,AAPL,BUY,5,190.00
 * 2026-02-01,AAPL,SELL,8,201.50
 * </pre>
 *
 * <p>The first line is always treated as a header and skipped, regardless
 * of its exact contents. Blank lines are ignored so a trailing newline
 * doesn't blow up the parse.</p>
 */
public final class TradeCsvReader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TradeCsvReader() {
    }

    public static List<Trade> read(Path csvPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            return read(reader);
        }
    }

    public static List<Trade> read(Reader rawReader) throws IOException {
        List<Trade> trades = new ArrayList<>();
        try (BufferedReader reader = rawReader instanceof BufferedReader br ? br : new BufferedReader(rawReader)) {
            String line = reader.readLine(); // header, discarded
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                trades.add(parseLine(line, lineNumber));
            }
        }
        return trades;
    }

    private static Trade parseLine(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 5) {
            throw new TradeCsvFormatException(lineNumber,
                    "expected 5 columns (date,symbol,side,quantity,price), found " + fields.length);
        }
        try {
            LocalDate date = LocalDate.parse(fields[0].trim(), DATE_FORMAT);
            String symbol = fields[1].trim();
            TradeSide side = TradeSide.fromString(fields[2].trim());
            BigDecimal quantity = new BigDecimal(fields[3].trim());
            BigDecimal price = new BigDecimal(fields[4].trim());
            return new Trade(date, symbol, side, quantity, price);
        } catch (RuntimeException e) {
            throw new TradeCsvFormatException(lineNumber, e.getMessage(), e);
        }
    }
}
