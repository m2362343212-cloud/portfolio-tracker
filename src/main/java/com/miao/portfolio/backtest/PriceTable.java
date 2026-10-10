package com.miao.portfolio.backtest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Prices for several assets on a common set of dates, read from the same
 * long-format CSV the optimizer uses ({@code date,symbol,price}).
 *
 * <p>Unlike {@code HistoricalPriceCsvReader}, this keeps the dates, because a
 * backtest has to know <em>when</em> each return happened. Only dates on which
 * every symbol has a price are kept, so all series line up.</p>
 *
 * <p>Use adjusted close prices (dividends reinvested). Otherwise bond and
 * equity funds look worse than they really were.</p>
 */
public final class PriceTable {

    private final List<LocalDate> dates;
    private final List<String> symbols;
    private final double[][] prices; // prices[t][i]

    PriceTable(List<LocalDate> dates, List<String> symbols, double[][] prices) {
        if (dates.size() != prices.length) {
            throw new IllegalArgumentException("one row of prices per date is required");
        }
        this.dates = List.copyOf(dates);
        this.symbols = List.copyOf(symbols);
        this.prices = prices;
    }

    public static PriceTable read(Path csv) throws IOException {
        try (Reader r = Files.newBufferedReader(csv)) {
            return read(r);
        }
    }

    public static PriceTable read(Reader raw) throws IOException {
        Map<String, TreeMap<LocalDate, Double>> bySymbol = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(raw)) {
            String line = reader.readLine(); // header
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split(",", -1);
                if (f.length != 3) {
                    throw new IllegalArgumentException("line " + lineNumber + ": expected date,symbol,price");
                }
                double price = Double.parseDouble(f[2].trim());
                if (!(price > 0)) {
                    throw new IllegalArgumentException("line " + lineNumber + ": price must be positive");
                }
                bySymbol.computeIfAbsent(f[1].trim().toUpperCase(), k -> new TreeMap<>())
                        .put(LocalDate.parse(f[0].trim()), price);
            }
        }
        if (bySymbol.size() < 2) {
            throw new IllegalArgumentException("need prices for at least 2 symbols");
        }
        TreeSet<LocalDate> common = null;
        for (TreeMap<LocalDate, Double> series : bySymbol.values()) {
            if (common == null) {
                common = new TreeSet<>(series.keySet());
            } else {
                common.retainAll(series.keySet());
            }
        }
        List<String> symbols = new ArrayList<>(bySymbol.keySet());
        List<LocalDate> dates = new ArrayList<>(common);
        double[][] prices = new double[dates.size()][symbols.size()];
        for (int t = 0; t < dates.size(); t++) {
            for (int i = 0; i < symbols.size(); i++) {
                prices[t][i] = bySymbol.get(symbols.get(i)).get(dates.get(t));
            }
        }
        return new PriceTable(dates, symbols, prices);
    }

    /** Keeps the last available date of each calendar month, so daily data becomes monthly. */
    public PriceTable monthEnds() {
        List<LocalDate> keptDates = new ArrayList<>();
        List<double[]> keptRows = new ArrayList<>();
        for (int t = 0; t < dates.size(); t++) {
            boolean lastOfMonth = t == dates.size() - 1
                    || !YearMonth.from(dates.get(t)).equals(YearMonth.from(dates.get(t + 1)));
            if (lastOfMonth) {
                keptDates.add(dates.get(t));
                keptRows.add(prices[t]);
            }
        }
        return new PriceTable(keptDates, symbols, keptRows.toArray(new double[0][]));
    }

    /** Simple returns between consecutive dates: returns[t][i] is the return from date t to t+1. */
    public double[][] returns() {
        double[][] r = new double[dates.size() - 1][symbols.size()];
        for (int t = 0; t + 1 < dates.size(); t++) {
            for (int i = 0; i < symbols.size(); i++) {
                r[t][i] = prices[t + 1][i] / prices[t][i] - 1.0;
            }
        }
        return r;
    }

    public List<LocalDate> getDates() {
        return dates;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public int size() {
        return dates.size();
    }
}
