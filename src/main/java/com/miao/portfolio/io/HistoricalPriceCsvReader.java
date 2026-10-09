package com.miao.portfolio.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads a historical price time series from a simple long-format CSV:
 *
 * <pre>
 * date,symbol,price
 * 2026-01-02,AAPL,182.10
 * 2026-01-02,MSFT,365.00
 * 2026-01-03,AAPL,183.40
 * ...
 * </pre>
 *
 * <p>Rows may arrive in any order and cover any subset of symbols on a
 * given date. To guarantee every symbol's resulting series has the same
 * length -- a requirement of {@link com.miao.portfolio.optimize.ReturnStatistics}
 * -- only dates on which <em>every</em> symbol in the file has a price are
 * kept: an inner join across symbols, not a union padded with placeholder
 * values, so a symbol with a gap doesn't silently corrupt the alignment
 * every other symbol's returns depend on.</p>
 */
public final class HistoricalPriceCsvReader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private HistoricalPriceCsvReader() {
    }

    public static Map<String, double[]> read(Path csvPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            return read(reader);
        }
    }

    public static Map<String, double[]> read(Reader rawReader) throws IOException {
        Map<String, TreeMap<LocalDate, Double>> pricesBySymbol = new LinkedHashMap<>();
        try (BufferedReader reader = rawReader instanceof BufferedReader br ? br : new BufferedReader(rawReader)) {
            String line = reader.readLine(); // header, discarded
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                parseLineInto(pricesBySymbol, line, lineNumber);
            }
        }

        if (pricesBySymbol.isEmpty()) {
            return Map.of();
        }
        return alignOnCommonDates(pricesBySymbol);
    }

    private static void parseLineInto(Map<String, TreeMap<LocalDate, Double>> pricesBySymbol, String line,
                                       int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 3) {
            throw new HistoricalPriceCsvFormatException(lineNumber,
                    "expected 3 columns (date,symbol,price), found " + fields.length);
        }
        try {
            LocalDate date = LocalDate.parse(fields[0].trim(), DATE_FORMAT);
            String symbol = fields[1].trim().toUpperCase();
            double price = Double.parseDouble(fields[2].trim());
            pricesBySymbol.computeIfAbsent(symbol, s -> new TreeMap<>()).put(date, price);
        } catch (RuntimeException e) {
            throw new HistoricalPriceCsvFormatException(lineNumber, e.getMessage(), e);
        }
    }

    private static Map<String, double[]> alignOnCommonDates(Map<String, TreeMap<LocalDate, Double>> pricesBySymbol) {
        Set<LocalDate> commonDates = null;
        for (TreeMap<LocalDate, Double> series : pricesBySymbol.values()) {
            if (commonDates == null) {
                commonDates = new TreeSet<>(series.keySet());
            } else {
                commonDates.retainAll(series.keySet());
            }
        }
        List<LocalDate> orderedDates = new ArrayList<>(commonDates);
        Collections.sort(orderedDates);

        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, TreeMap<LocalDate, Double>> entry : pricesBySymbol.entrySet()) {
            double[] prices = new double[orderedDates.size()];
            for (int i = 0; i < orderedDates.size(); i++) {
                prices[i] = entry.getValue().get(orderedDates.get(i));
            }
            result.put(entry.getKey(), prices);
        }
        return result;
    }
}
