package com.miao.portfolio.backtest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Walk-forward backtest with periodic rebalancing.
 *
 * <p>At the end of each period, the strategy sees only the previous
 * {@code lookback} returns, sets target weights, and the portfolio is held
 * for the next period. No future data is ever used. Between rebalances the
 * weights drift with prices; trading back to target costs
 * {@code costBps} basis points on the amount traded.</p>
 */
public final class Backtester {

    private final int lookback;
    private final double costRate;

    /**
     * @param lookback number of past periods each decision may use (e.g. 36 months)
     * @param costBps  one-way trading cost in basis points (10 = 0.10% of the amount traded)
     */
    public Backtester(int lookback, double costBps) {
        if (lookback < 2) {
            throw new IllegalArgumentException("lookback must be at least 2 periods");
        }
        if (costBps < 0) {
            throw new IllegalArgumentException("trading cost cannot be negative");
        }
        this.lookback = lookback;
        this.costRate = costBps / 10_000.0;
    }

    public BacktestResult run(PriceTable table, AllocationStrategy strategy) {
        double[][] r = table.returns();
        List<String> symbols = table.getSymbols();
        int n = symbols.size();
        int periods = r.length - lookback;
        if (periods < 1) {
            throw new IllegalArgumentException("not enough history: need more than " + lookback + " returns, have " + r.length);
        }
        double[] net = new double[periods];
        double[] turnover = new double[periods];
        double[][] targets = new double[periods][];
        double[] drifted = new double[n]; // starts in cash: first trade buys the whole portfolio

        for (int k = 0; k < periods; k++) {
            int t = lookback + k; // period t is the one being invested in
            double[][] past = Arrays.copyOfRange(r, t - lookback, t);
            double[] w = strategy.weights(symbols, past);
            checkWeights(w, strategy.name());
            targets[k] = w;

            double traded = 0;
            for (int i = 0; i < n; i++) {
                traded += Math.abs(w[i] - drifted[i]);
            }
            turnover[k] = k == 0 ? 1.0 : traded / 2.0; // one-way; the first period is the initial purchase
            double cost = (k == 0 ? 1.0 : traded) * costRate;

            double gross = 0;
            for (int i = 0; i < n; i++) {
                gross += w[i] * r[t][i];
            }
            net[k] = (1 - cost) * (1 + gross) - 1;

            for (int i = 0; i < n; i++) { // weights drift with this period's returns
                drifted[i] = w[i] * (1 + r[t][i]) / (1 + gross);
            }
        }
        List<LocalDate> dates = table.getDates().subList(lookback + 1, lookback + 1 + periods);
        return new BacktestResult(strategy.name(), dates, net, turnover, targets);
    }

    private static void checkWeights(double[] w, String name) {
        double sum = 0;
        for (double x : w) {
            if (x < -1e-9 || Double.isNaN(x)) {
                throw new IllegalStateException(name + " produced an invalid weight " + x);
            }
            sum += x;
        }
        if (Math.abs(sum - 1) > 1e-6) {
            throw new IllegalStateException(name + " weights sum to " + sum + ", not 1");
        }
    }
}
