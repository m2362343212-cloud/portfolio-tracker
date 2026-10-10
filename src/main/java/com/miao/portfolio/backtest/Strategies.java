package com.miao.portfolio.backtest;

import com.miao.portfolio.optimize.OptimizationResult;
import com.miao.portfolio.optimize.PortfolioOptimizer;
import com.miao.portfolio.optimize.ReturnStatistics;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The allocation rules compared in the backtest. */
public final class Strategies {

    private Strategies() {
    }

    /** Same weight in every asset. The simplest benchmark, and famously hard to beat. */
    public static AllocationStrategy equalWeight() {
        return new AllocationStrategy() {
            public String name() {
                return "Equal weight";
            }

            public double[] weights(List<String> symbols, double[][] pastReturns) {
                double[] w = new double[symbols.size()];
                Arrays.fill(w, 1.0 / symbols.size());
                return w;
            }
        };
    }

    /** Fixed target weights, e.g. a 60/40 stock/bond mix. Symbols not listed get 0. */
    public static AllocationStrategy fixedWeights(String name, Map<String, Double> targets) {
        double total = targets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1.0) > 1e-9) {
            throw new IllegalArgumentException("fixed weights must sum to 1, got " + total);
        }
        return new AllocationStrategy() {
            public String name() {
                return name;
            }

            public double[] weights(List<String> symbols, double[][] pastReturns) {
                for (String s : targets.keySet()) {
                    if (!symbols.contains(s)) {
                        throw new IllegalArgumentException("benchmark symbol " + s + " is not in the price file");
                    }
                }
                double[] w = new double[symbols.size()];
                for (int i = 0; i < symbols.size(); i++) {
                    w[i] = targets.getOrDefault(symbols.get(i), 0.0);
                }
                return w;
            }
        };
    }

    /** Weights proportional to 1 / volatility: calmer assets get more money. */
    public static AllocationStrategy inverseVolatility() {
        return new AllocationStrategy() {
            public String name() {
                return "Inverse volatility";
            }

            public double[] weights(List<String> symbols, double[][] pastReturns) {
                ReturnStatistics stats = statistics(symbols, pastReturns);
                double[][] cov = stats.getCovariance();
                double[] w = new double[symbols.size()];
                double sum = 0;
                for (int i = 0; i < w.length; i++) {
                    w[i] = 1.0 / Math.sqrt(Math.max(cov[i][i], 1e-12));
                    sum += w[i];
                }
                for (int i = 0; i < w.length; i++) {
                    w[i] /= sum;
                }
                return w;
            }
        };
    }

    /** Long-only minimum-variance portfolio from the project's own optimizer. Uses only the covariance, not mean returns. */
    public static AllocationStrategy minimumVariance() {
        return new AllocationStrategy() {
            public String name() {
                return "Minimum variance";
            }

            public double[] weights(List<String> symbols, double[][] pastReturns) {
                OptimizationResult result = new PortfolioOptimizer(statistics(symbols, pastReturns)).minimumVariance();
                double[] w = new double[symbols.size()];
                for (int i = 0; i < w.length; i++) {
                    w[i] = result.getWeights().getOrDefault(symbols.get(i), 0.0);
                }
                return w;
            }
        };
    }

    static ReturnStatistics statistics(List<String> symbols, double[][] pastReturns) {
        Map<String, double[]> bySymbol = new LinkedHashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            double[] series = new double[pastReturns.length];
            for (int t = 0; t < pastReturns.length; t++) {
                series[t] = pastReturns[t][i];
            }
            bySymbol.put(symbols.get(i), series);
        }
        return ReturnStatistics.from(bySymbol);
    }
}
