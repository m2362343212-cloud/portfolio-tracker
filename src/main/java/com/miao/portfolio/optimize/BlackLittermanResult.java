package com.miao.portfolio.optimize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The posterior of a {@link BlackLittermanModel#combine} call: expected
 * returns and a covariance that already reflect both the market-implied
 * prior and the investor's views, ready to hand to {@link PortfolioOptimizer}.
 */
public final class BlackLittermanResult {

    private final List<String> symbols;
    private final double[] posteriorReturns;
    private final double[][] posteriorCovariance;

    BlackLittermanResult(List<String> symbols, double[] posteriorReturns, double[][] posteriorCovariance) {
        this.symbols = symbols;
        this.posteriorReturns = posteriorReturns.clone();
        this.posteriorCovariance = new double[posteriorCovariance.length][];
        for (int i = 0; i < posteriorCovariance.length; i++) {
            this.posteriorCovariance[i] = posteriorCovariance[i].clone();
        }
    }

    public List<String> getSymbols() {
        return symbols;
    }

    /** Posterior expected return per symbol, blending the market-implied prior with the supplied views. */
    public Map<String, Double> getPosteriorReturns() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            map.put(symbols.get(i), posteriorReturns[i]);
        }
        return Collections.unmodifiableMap(map);
    }

    public double[] getPosteriorReturnsArray() {
        return posteriorReturns.clone();
    }

    /** The posterior covariance: the prior covariance plus the estimation uncertainty the update leaves in the mean. */
    public double[][] getPosteriorCovariance() {
        double[][] copy = new double[posteriorCovariance.length][];
        for (int i = 0; i < posteriorCovariance.length; i++) {
            copy[i] = posteriorCovariance[i].clone();
        }
        return copy;
    }

    /** Packages this posterior as a {@link ReturnStatistics}, ready to optimize with {@link PortfolioOptimizer}. */
    public ReturnStatistics toReturnStatistics() {
        return ReturnStatistics.of(symbols, posteriorReturns, posteriorCovariance);
    }
}
