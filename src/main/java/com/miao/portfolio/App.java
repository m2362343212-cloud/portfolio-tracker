package com.miao.portfolio;

import com.miao.portfolio.io.TradeCsvReader;
import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Trade;
import com.miao.portfolio.report.PortfolioReportGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point: reads a trade CSV, applies it to a fresh
 * {@link Portfolio}, and prints a P&amp;L report.
 *
 * <p>Usage: {@code java -jar portfolio-tracker.jar [trades.csv] [prices.csv]}</p>
 *
 * <p>Both arguments are optional. With no arguments, a bundled sample
 * trade blotter and price sheet are used so the jar produces useful
 * output immediately. {@code prices.csv} is a simple {@code symbol,price}
 * file, one line per open symbol.</p>
 */
public final class App {

    public static void main(String[] args) throws IOException {
        List<Trade> trades;
        Map<String, BigDecimal> marketPrices;

        if (args.length >= 1) {
            trades = TradeCsvReader.read(Path.of(args[0]));
        } else {
            trades = TradeCsvReader.read(openBundledReader("/sample-trades.csv"));
        }

        if (args.length >= 2) {
            marketPrices = readPrices(Path.of(args[1]));
        } else {
            marketPrices = readPrices(openBundledReader("/sample-prices.csv"));
        }

        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(trades);

        System.out.print(PortfolioReportGenerator.generate(portfolio, marketPrices));
    }

    private static Reader openBundledReader(String resourcePath) {
        InputStream stream = App.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new UncheckedIOException(new IOException("Bundled resource not found: " + resourcePath));
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static Map<String, BigDecimal> readPrices(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return readPrices(reader);
        }
    }

    private static Map<String, BigDecimal> readPrices(Reader rawReader) throws IOException {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        try (var reader = new java.io.BufferedReader(rawReader)) {
            String line = reader.readLine(); // header, discarded
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                prices.put(fields[0].trim().toUpperCase(), new BigDecimal(fields[1].trim()));
            }
        }
        return prices;
    }
}
