package com.miao.portfolio;

import com.miao.portfolio.io.TradeCsvReader;
import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Trade;
import com.miao.portfolio.report.PortfolioReportGenerator;
import com.miao.portfolio.web.PortfolioWebServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point: reads a trade CSV, applies it to a fresh
 * {@link Portfolio}, and either prints a text P&amp;L report or serves it
 * as a live local web dashboard.
 *
 * <p>Usage: {@code java -jar portfolio-tracker.jar [--web] [trades.csv] [prices.csv]}</p>
 *
 * <p>{@code trades.csv} and {@code prices.csv} are optional; with neither
 * given, a bundled sample trade blotter and price sheet are used so the
 * jar produces useful output immediately. {@code prices.csv} is a simple
 * {@code symbol,price} file, one line per open symbol. Passing
 * {@code --web} (in any position) starts a browser dashboard on
 * {@code http://localhost:8080/} instead of printing to the console.</p>
 */
public final class App {

    private static final int WEB_PORT = 8080;

    public static void main(String[] args) throws IOException {
        boolean webMode = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--web")) {
                webMode = true;
            } else {
                positional.add(arg);
            }
        }

        List<Trade> trades;
        Map<String, BigDecimal> marketPrices;

        if (!positional.isEmpty()) {
            trades = TradeCsvReader.read(Path.of(positional.get(0)));
        } else {
            trades = TradeCsvReader.read(openBundledReader("/sample-trades.csv"));
        }

        if (positional.size() >= 2) {
            marketPrices = readPrices(Path.of(positional.get(1)));
        } else {
            marketPrices = readPrices(openBundledReader("/sample-prices.csv"));
        }

        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(trades);

        if (webMode) {
            PortfolioWebServer server = new PortfolioWebServer(portfolio, marketPrices, WEB_PORT);
            server.start();
            System.out.println("Portfolio dashboard running at " + server.getUrl());
            System.out.println("Press Ctrl+C to stop.");
        } else {
            System.out.print(PortfolioReportGenerator.generate(portfolio, marketPrices));
        }
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
