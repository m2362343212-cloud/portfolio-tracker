package com.miao.portfolio.backtest;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line for the multi-asset backtest.
 *
 * <pre>
 * java -jar portfolio-tracker.jar --backtest prices.csv [--benchmark XIC=0.6,XBB=0.4]
 *      [--lookback 36] [--cost-bps 10] [--risk-free 0.02] [--max-weight 0.4] [--out backtest-results.csv]
 * </pre>
 *
 * Prices may be daily or monthly; they are converted to month-end prices first.
 */
public final class BacktestCli {

    private BacktestCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: --backtest prices.csv [--benchmark A=0.6,B=0.4] [--lookback 36] [--cost-bps 10] [--risk-free 0.02] [--max-weight 0.4] [--out file.csv]");
        }
        Path pricesPath = Path.of(args[0]);
        Map<String, Double> benchmark = null;
        int lookback = 36;
        double costBps = 10;
        double riskFree = 0.0;
        Double maxWeight = null;
        Path out = Path.of("backtest-results.csv");
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value after " + args[i]);
            }
            String v = args[i + 1];
            switch (args[i]) {
                case "--benchmark" -> benchmark = parseWeights(v);
                case "--lookback" -> lookback = Integer.parseInt(v);
                case "--cost-bps" -> costBps = Double.parseDouble(v);
                case "--risk-free" -> riskFree = Double.parseDouble(v);
                case "--out" -> out = Path.of(v);
                case "--max-weight" -> maxWeight = Double.parseDouble(v);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }

        PriceTable monthly = PriceTable.read(pricesPath).monthEnds();
        List<AllocationStrategy> strategies = new ArrayList<>();
        strategies.add(Strategies.equalWeight());
        if (benchmark != null) {
            strategies.add(Strategies.fixedWeights("Fixed benchmark", benchmark));
        }
        strategies.add(Strategies.inverseVolatility());
        strategies.add(Strategies.minimumVariance());
        if (maxWeight != null) {
            strategies.add(new CappedMinimumVariance(maxWeight));
        }

        Backtester backtester = new Backtester(lookback, costBps);
        List<BacktestResult> results = new ArrayList<>();
        for (AllocationStrategy s : strategies) {
            results.add(backtester.run(monthly, s));
        }
        System.out.print(BacktestReport.text(monthly, results, lookback, costBps, riskFree));
        writeGrowthCsv(out, results);
        System.out.println("Growth of $1 per strategy written to " + out);
    }

    static Map<String, Double> parseWeights(String spec) {
        Map<String, Double> w = new LinkedHashMap<>();
        for (String part : spec.split(",")) {
            String[] kv = part.split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("benchmark must look like XIC=0.6,XBB=0.4");
            }
            w.put(kv[0].trim().toUpperCase(), Double.parseDouble(kv[1].trim()));
        }
        return w;
    }

    static void writeGrowthCsv(Path out, List<BacktestResult> results) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            StringBuilder header = new StringBuilder("date");
            for (BacktestResult r : results) {
                header.append(',').append(r.strategy());
            }
            pw.println(header);
            double[] growth = new double[results.size()];
            java.util.Arrays.fill(growth, 1.0);
            int periods = results.get(0).netReturns().length;
            for (int t = 0; t < periods; t++) {
                StringBuilder row = new StringBuilder(results.get(0).dates().get(t).toString());
                for (int s = 0; s < results.size(); s++) {
                    growth[s] *= 1 + results.get(s).netReturns()[t];
                    row.append(',').append(String.format("%.6f", growth[s]));
                }
                pw.println(row);
            }
        }
    }
}
