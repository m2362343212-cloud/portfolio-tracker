package com.miao.portfolio.optimize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The output of {@link PortfolioOptimizer}: a weight per symbol plus the portfolio stats they produce. */
public final class OptimizationResult {

    private final Map<String, Double> weights;
    private final double expectedReturn;
    private final double variance;
    private final int iterations;

    OptimizationResult(Map<String, Double> weights, double expectedReturn, double variance, int iterations) {
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
        this.expectedReturn = expectedReturn;
        this.variance = variance;
        this.iterations = iterations;
    }

    /** Portfolio weight per symbol; sums to 1.0 (within floating-point tolerance), none negative. */
    public Map<String, Double> getWeights() {
        return weights;
    }

    public double getExpectedReturn() {
        return expectedReturn;
    }

    public double getVariance() {
        return variance;
    }

    public double getStandardDeviation() {
        return Math.sqrt(variance);
    }

    /** How many projected-gradient-descent steps ran before convergence (or the iteration cap). */
    public int getIterations() {
        return iterations;
    }
}
