package com.miao.portfolio;

import com.miao.portfolio.io.HistoricalPriceCsvReader;
import com.miao.portfolio.io.TradeCsvReader;
import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Trade;
import com.miao.portfolio.optimize.BlackLittermanModel;
import com.miao.portfolio.optimize.BlackLittermanResult;
import com.miao.portfolio.optimize.OptimizationResult;
import com.miao.portfolio.optimize.PortfolioOptimizer;
import com.miao.portfolio.optimize.ReturnStatistics;
import com.miao.portfolio.optimize.Returns;
import com.miao.portfolio.report.OptimizationReportGenerator;
import com.miao.portfolio.report.PortfolioReportGenerator;
import com.miao.portfolio.web.HtmlReportRenderer;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point: reads a trade CSV, applies it to a fresh
 * {@link Portfolio}, and either prints a text P&amp;L report or serves it
 * as a live local web dashboard.
 *
 * <p>Usage: {@code java -jar portfolio-tracker.jar [--web] [--optimize] [--bayesian] [trades.csv] [prices.csv] [historical-prices.csv]}</p>
 *
 * <p>{@code trades.csv} and {@code prices.csv} are optional; with neither
 * given, a bundled sample trade blotter and price sheet are used so the
 * jar produces useful output immediately. {@code prices.csv} is a simple
 * {@code symbol,price} file, one line per open symbol. Passing
 * {@code --web} (in any position) starts a browser dashboard on
 * {@code http://localhost:8080/} instead of printing to the console.
 * Passing {@code --optimize} additionally computes a suggested long-only,
 * minimum-variance rebalancing of the open positions, computed by {@link
 * PortfolioOptimizer} from a historical price series -- {@code
 * historical-prices.csv} if given (a third positional argument, a
 * {@code date,symbol,price} file), or a bundled sample otherwise. Passing
 * {@code --bayesian} instead runs the same optimizer, but on expected
 * returns from {@link BlackLittermanModel} (market-implied equilibrium
 * returns updated with one illustrative view) rather than the raw
 * historical sample mean. Both flags print to the console as before; when
 * combined with {@code --web}, the same results also appear in an
 * "Optimization" section on the dashboard itself, rather than only in
 * console output the browser never sees.</p>
 */
public final class App {

    private static final int WEB_PORT = 8080;

    // Textbook default values for the Black-Litterman demo below; a real
    // caller should calibrate riskAversion via BlackLittermanModel.impliedRiskAversion(...)
    // rather than use a fixed constant.
    private static final double BAYESIAN_DEMO_RISK_AVERSION = 2.5;
    private static final double BAYESIAN_DEMO_TAU = 0.05;

    public static void main(String[] args) throws IOException {
        boolean webMode = false;
        boolean optimizeMode = false;
        boolean bayesianMode = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--web" -> webMode = true;
                case "--optimize" -> optimizeMode = true;
                case "--bayesian" -> bayesianMode = true;
                default -> positional.add(arg);
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

        if (!webMode) {
            System.out.print(PortfolioReportGenerator.generate(portfolio, marketPrices));
        }

        // Collected only when webMode is on, so --optimize/--bayesian results show up on the
        // dashboard itself rather than only in console output the browser never sees.
        List<HtmlReportRenderer.ReturnsTable> returnsTables = new ArrayList<>();
        List<HtmlReportRenderer.WeightTable> weightTables = new ArrayList<>();

        if (optimizeMode || bayesianMode) {
            Map<String, double[]> historicalPrices;
            if (positional.size() >= 3) {
                historicalPrices = HistoricalPriceCsvReader.read(Path.of(positional.get(2)));
            } else {
                historicalPrices = HistoricalPriceCsvReader.read(openBundledReader("/sample-historical-prices.csv"));
            }
            if (optimizeMode) {
                runOptimization(historicalPrices, webMode, weightTables);
            }
            if (bayesianMode) {
                runBayesianOptimization(historicalPrices, webMode, returnsTables, weightTables);
            }
        }

        if (webMode) {
            PortfolioWebServer server = new PortfolioWebServer(portfolio, marketPrices, returnsTables, weightTables, WEB_PORT);
            server.start();
            System.out.println("Portfolio dashboard running at " + server.getUrl());
            System.out.println("Press Ctrl+C to stop.");
        }
    }

    private static void runBayesianOptimization(Map<String, double[]> historicalPrices, boolean webMode,
                                                  List<HtmlReportRenderer.ReturnsTable> returnsTables,
                                                  List<HtmlReportRenderer.WeightTable> weightTables) {
        if (historicalPrices.size() < 2) {
            System.out.println("--bayesian needs at least 2 symbols with aligned historical prices; skipping.");
            return;
        }

        Map<String, double[]> periodicReturns = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : historicalPrices.entrySet()) {
            periodicReturns.put(entry.getKey(), Returns.simpleReturns(entry.getValue()));
        }
        ReturnStatistics prior = ReturnStatistics.from(periodicReturns);
        List<String> symbols = prior.getSymbols();
        int n = prior.size();

        // Illustrative "market" weights: a real caller would supply actual
        // market-cap weights here. This demo has none, so it falls back to
        // equal weighting across whatever symbols are present -- the same
        // "clearly synthetic, not a real claim about any company" spirit as
        // the rest of the bundled sample data.
        double[] marketWeights = new double[n];
        Arrays.fill(marketWeights, 1.0 / n);

        BlackLittermanModel model = new BlackLittermanModel(prior, marketWeights, BAYESIAN_DEMO_RISK_AVERSION, BAYESIAN_DEMO_TAU);

        System.out.println("Market-implied equilibrium returns (the Black-Litterman prior, before any view):");
        double[] impliedReturns = model.getImpliedEquilibriumReturns();
        for (int i = 0; i < n; i++) {
            System.out.printf("  %-8s %7.4f%%%n", symbols.get(i), impliedReturns[i] * 100);
        }

        // One illustrative relative view for the demo -- a placeholder, not
        // a real forecast: a real caller would pass in their own views.
        BlackLittermanModel.View illustrativeView = model.relative(0, 1, 0.02, 0.0004);
        System.out.printf("Applying an illustrative view: %s will outperform %s by 2.00%% (a placeholder view for this demo, not a real forecast)%n",
                symbols.get(0), symbols.get(1));

        BlackLittermanResult result = model.combine(List.of(illustrativeView));
        double[] posteriorReturns = result.getPosteriorReturnsArray();
        System.out.println("Posterior expected returns (prior blended with the view above):");
        double maxPosteriorReturn = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            System.out.printf("  %-8s %7.4f%%%n", symbols.get(i), posteriorReturns[i] * 100);
            maxPosteriorReturn = Math.max(maxPosteriorReturn, posteriorReturns[i]);
        }

        ReturnStatistics posteriorStats = result.toReturnStatistics();

        // Minimum variance ignores expected return entirely by construction
        // (see PortfolioOptimizer's own Javadoc), so it's printed only as a
        // baseline -- it will come out identical whether or not any view
        // was ever applied. targetReturn is the one that actually responds
        // to the Bayesian update: it's asked to hit the posterior's own
        // best expected return, which the view above pushed higher for the
        // outperforming symbol, so satisfying it means tilting toward that
        // symbol -- trading some of the min-variance portfolio's safety for
        // the return the view implies.
        OptimizationResult minVariance = new PortfolioOptimizer(posteriorStats).minimumVariance();
        OptimizationResult returnSeeking = new PortfolioOptimizer(posteriorStats).targetReturn(maxPosteriorReturn, 1e7);

        System.out.print(OptimizationReportGenerator.generate(
                "MINIMUM VARIANCE on the BL posterior (baseline -- ignores expected return by construction)", minVariance));
        System.out.print(OptimizationReportGenerator.generate(
                "RETURN-SEEKING on the BL posterior (targets the view-favored symbol's posterior return)", returnSeeking));

        if (webMode) {
            String viewNote = "Illustrative view (a placeholder, not a real forecast): %s will outperform %s by 2.00%%"
                    .formatted(symbols.get(0), symbols.get(1));
            returnsTables.add(new HtmlReportRenderer.ReturnsTable(
                    "Black-Litterman expected returns", symbols,
                    "Prior (implied)", impliedReturns, "Posterior (with view)", posteriorReturns, viewNote));
            weightTables.add(new HtmlReportRenderer.WeightTable(
                    "Minimum variance on the BL posterior (baseline)", minVariance));
            weightTables.add(new HtmlReportRenderer.WeightTable(
                    "Return-seeking on the BL posterior (targets the view-favored symbol)", returnSeeking));
        }
    }

    private static void runOptimization(Map<String, double[]> historicalPrices, boolean webMode,
                                         List<HtmlReportRenderer.WeightTable> weightTables) {
        if (historicalPrices.size() < 2) {
            System.out.println("--optimize needs at least 2 symbols with aligned historical prices; skipping.");
            return;
        }

        Map<String, double[]> periodicReturns = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : historicalPrices.entrySet()) {
            periodicReturns.put(entry.getKey(), Returns.simpleReturns(entry.getValue()));
        }

        ReturnStatistics stats = ReturnStatistics.from(periodicReturns);
        PortfolioOptimizer optimizer = new PortfolioOptimizer(stats);

        OptimizationResult minVariance = optimizer.minimumVariance();
        System.out.print(OptimizationReportGenerator.generate(
                "SUGGESTED WEIGHTS (long-only, minimum variance)", minVariance));

        if (webMode) {
            weightTables.add(new HtmlReportRenderer.WeightTable(
                    "Suggested weights (long-only, minimum variance)", minVariance));
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
