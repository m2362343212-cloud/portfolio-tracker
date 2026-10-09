package com.miao.portfolio.optimize;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sample mean return and sample covariance matrix of a set of
 * instruments' periodic returns -- the two inputs every mean-variance
 * optimizer needs, and nothing else.
 *
 * <p>Symbol order is taken from the iteration order of the map passed to
 * {@link #from}, so callers that care about a stable, human-meaningful
 * order (e.g. first-traded order, matching {@link
 * com.miao.portfolio.model.Portfolio}) should pass a {@link
 * java.util.LinkedHashMap}.</p>
 */
public final class ReturnStatistics {

    private final List<String> symbols;
    private final double[] meanReturns;
    private final double[][] covariance;

    private ReturnStatistics(List<String> symbols, double[] meanReturns, double[][] covariance) {
        this.symbols = symbols;
        this.meanReturns = meanReturns;
        this.covariance = covariance;
    }

    /**
     * Builds statistics from a period-aligned return series per symbol --
     * {@code periodicReturns.get(symbol)[t]} must be that symbol's return
     * for the same period {@code t} across every symbol. {@link Returns}
     * turns a raw price series into the simple returns this expects.
     *
     * @throws IllegalArgumentException if the map is empty, any two symbols'
     *         series have different lengths, or there are fewer than 2
     *         periods (sample covariance needs at least 2 observations)
     */
    public static ReturnStatistics from(Map<String, double[]> periodicReturns) {
        if (periodicReturns.isEmpty()) {
            throw new IllegalArgumentException("periodicReturns must not be empty");
        }

        List<String> symbols = List.copyOf(periodicReturns.keySet());
        int n = symbols.size();
        int periods = periodicReturns.get(symbols.get(0)).length;
        if (periods < 2) {
            throw new IllegalArgumentException(
                    "need at least 2 return periods to estimate a covariance, got " + periods);
        }

        double[][] returns = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] series = periodicReturns.get(symbols.get(i));
            if (series.length != periods) {
                throw new IllegalArgumentException(
                        "all symbols must have the same number of return periods; %s has %d, expected %d"
                                .formatted(symbols.get(i), series.length, periods));
            }
            returns[i] = series;
        }

        double[] mean = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (double r : returns[i]) {
                sum += r;
            }
            mean[i] = sum / periods;
        }

        // Sample covariance (Bessel's correction, n-1 denominator) -- the
        // unbiased estimator, and the standard choice when the "true"
        // covariance is itself unknown and being estimated from a sample.
        double[][] covariance = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double sum = 0.0;
                for (int t = 0; t < periods; t++) {
                    sum += (returns[i][t] - mean[i]) * (returns[j][t] - mean[j]);
                }
                double value = sum / (periods - 1);
                covariance[i][j] = value;
                covariance[j][i] = value;
            }
        }

        return new ReturnStatistics(symbols, mean, covariance);
    }

    /**
     * Builds statistics from an already-estimated mean and covariance,
     * rather than raw historical returns -- the bridge for a model like
     * {@link BlackLittermanModel} (whose whole point is to produce a
     * <em>better</em> expected-return estimate than the raw sample mean)
     * to hand its output straight to {@link PortfolioOptimizer}.
     *
     * <p>The covariance is symmetrized ({@code (cov[i][j] + cov[j][i]) / 2})
     * before storage, defensively guarding against the tiny floating-point
     * asymmetry that repeated matrix arithmetic (as in Black-Litterman's
     * posterior-covariance formula) can otherwise introduce.</p>
     *
     * @throws IllegalArgumentException if symbols/meanReturns/covariance
     *         have inconsistent sizes, or covariance isn't square
     */
    public static ReturnStatistics of(List<String> symbols, double[] meanReturns, double[][] covariance) {
        int n = symbols.size();
        if (meanReturns.length != n) {
            throw new IllegalArgumentException(
                    "meanReturns length (%d) must match symbols size (%d)".formatted(meanReturns.length, n));
        }
        if (covariance.length != n) {
            throw new IllegalArgumentException(
                    "covariance row count (%d) must match symbols size (%d)".formatted(covariance.length, n));
        }
        double[][] symmetrized = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (covariance[i].length != n) {
                throw new IllegalArgumentException("covariance must be square (%d x %d), row %d has %d columns"
                        .formatted(n, n, i, covariance[i].length));
            }
            for (int j = 0; j < n; j++) {
                symmetrized[i][j] = (covariance[i][j] + covariance[j][i]) / 2.0;
            }
        }
        return new ReturnStatistics(List.copyOf(symbols), meanReturns.clone(), symmetrized);
    }

    public List<String> getSymbols() {
        return symbols;
    }

    /** Mean return per symbol, in the same order as {@link #getSymbols()}. */
    public double[] getMeanReturns() {
        return meanReturns.clone();
    }

    /** The symmetric n x n sample covariance matrix, in symbol order. */
    public double[][] getCovariance() {
        double[][] copy = new double[covariance.length][];
        for (int i = 0; i < covariance.length; i++) {
            copy[i] = covariance[i].clone();
        }
        return copy;
    }

    public int size() {
        return symbols.size();
    }
}
