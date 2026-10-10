package com.miao.portfolio.backtest;

import java.util.List;

/** Plain-text summary table of a backtest. */
public final class BacktestReport {

    private BacktestReport() {
    }

    public static String text(PriceTable monthly, List<BacktestResult> results, int lookback, double costBps, double riskFree) {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(92) + "\n";
        sb.append(line).append("MULTI-ASSET BACKTEST (monthly rebalancing, walk-forward)\n").append(line);
        sb.append("Assets: ").append(String.join(", ", monthly.getSymbols())).append('\n');
        BacktestResult first = results.get(0);
        sb.append("Period: ").append(first.dates().get(0)).append(" to ")
                .append(first.dates().get(first.dates().size() - 1))
                .append(" (").append(first.netReturns().length).append(" months)\n");
        sb.append("Each decision uses the previous ").append(lookback).append(" months only; trading cost ")
                .append(costBps).append(" bps one-way; risk-free rate ").append(String.format("%.2f%%", riskFree * 100)).append("\n\n");
        sb.append(String.format("%-22s %10s %10s %8s %10s %12s%n",
                "STRATEGY", "RETURN/YR", "VOL/YR", "SHARPE", "MAX DD", "TURNOVER/YR"));
        sb.append("-".repeat(92)).append('\n');
        for (BacktestResult r : results) {
            PerformanceStats p = PerformanceStats.of(r, 12, riskFree);
            sb.append(String.format("%-22s %9.2f%% %9.2f%% %8.2f %9.2f%% %11.0f%%%n",
                    r.strategy(), p.annualReturn() * 100, p.annualVolatility() * 100, p.sharpe(),
                    p.maxDrawdown() * 100, p.annualTurnover() * 100));
        }
        sb.append("\nAverage weights over the backtest:\n");
        for (BacktestResult r : results) {
            sb.append(String.format("  %-20s", r.strategy()));
            double[][] w = r.weights();
            for (int i = 0; i < monthly.getSymbols().size(); i++) {
                double avg = 0;
                for (double[] row : w) {
                    avg += row[i];
                }
                sb.append(String.format(" %s %5.1f%%", monthly.getSymbols().get(i), avg / w.length * 100));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
