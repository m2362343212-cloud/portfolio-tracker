package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioOptimizerTest {

    private static double portfolioVariance(double[][] covariance, double[] weights) {
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            double rowSum = 0;
            for (int j = 0; j < weights.length; j++) {
                rowSum += covariance[i][j] * weights[j];
            }
            total += weights[i] * rowSum;
        }
        return total;
    }

    @Test
    void equalVarianceTwoAssetPortfolioSplitsFiftyFifty() {
        // A and B are constructed (see ReturnStatisticsTest's method for how the
        // math works out) to have equal variance but negative covariance. For
        // any two assets with equal variance, f(w) = Var(w*A + (1-w)*B) is
        // symmetric about w = 0.5 regardless of their covariance, so the
        // unconstrained -- and here, since it's interior, also the
        // long-only-constrained -- minimum-variance point is the 50/50 split.
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.02, -0.01, 0.03, 0.00, 0.01});
        returns.put("B", new double[]{0.01, 0.02, -0.01, 0.03, 0.00});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        OptimizationResult result = new PortfolioOptimizer(stats).minimumVariance();

        assertEquals(0.5, result.getWeights().get("A"), 1e-3);
        assertEquals(0.5, result.getWeights().get("B"), 1e-3);
    }

    @Test
    void minimumVarianceFavorsTheLowerVarianceAsset() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        // Small, steady swings.
        returns.put("STEADY", new double[]{0.001, -0.002, 0.002, -0.001, 0.0015, -0.0005});
        // Same rough shape, 20x larger swings -- much higher variance.
        returns.put("VOLATILE", new double[]{0.02, -0.04, 0.04, -0.02, 0.03, -0.01});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        OptimizationResult result = new PortfolioOptimizer(stats).minimumVariance();

        double steadyWeight = result.getWeights().get("STEADY");
        double volatileWeight = result.getWeights().get("VOLATILE");
        assertTrue(steadyWeight > volatileWeight,
                "the lower-variance asset should get more weight: STEADY=" + steadyWeight + " VOLATILE=" + volatileWeight);
    }

    @Test
    void everyResultIsAValidLongOnlyFullyInvestedPortfolio() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.001, -0.002, 0.002, -0.001, 0.0015, -0.0005});
        returns.put("B", new double[]{0.02, -0.04, 0.04, -0.02, 0.03, -0.01});
        returns.put("C", new double[]{0.005, 0.003, -0.004, 0.006, -0.002, 0.001});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        OptimizationResult result = new PortfolioOptimizer(stats).minimumVariance();

        double sum = result.getWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "weights must sum to 1 (fully invested)");
        result.getWeights().forEach((symbol, weight) ->
                assertTrue(weight >= -1e-9, symbol + " has a negative (short) weight: " + weight));
    }

    @Test
    void optimizedPortfolioHasNoMoreVarianceThanTheNaiveEqualWeightPortfolio() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.001, -0.002, 0.002, -0.001, 0.0015, -0.0005});
        returns.put("B", new double[]{0.02, -0.04, 0.04, -0.02, 0.03, -0.01});
        returns.put("C", new double[]{0.005, 0.003, -0.004, 0.006, -0.002, 0.001});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        OptimizationResult result = new PortfolioOptimizer(stats).minimumVariance();

        double[][] cov = stats.getCovariance();
        double equalWeightVariance = portfolioVariance(cov, new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3});

        assertTrue(result.getVariance() <= equalWeightVariance + 1e-12,
                "optimization should never leave the portfolio worse off than equal-weighting: optimized="
                        + result.getVariance() + " equalWeight=" + equalWeightVariance);
        assertTrue(result.getVariance() < equalWeightVariance,
                "for this asymmetric covariance structure, optimization should strictly help");
    }

    @Test
    void convergesWithinTheIterationBudget() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.001, -0.002, 0.002, -0.001, 0.0015, -0.0005});
        returns.put("B", new double[]{0.02, -0.04, 0.04, -0.02, 0.03, -0.01});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        OptimizationResult result = new PortfolioOptimizer(stats).minimumVariance();

        assertTrue(result.getIterations() < 5_000, "should converge once the iterate stops moving, not hit the iteration cap");
    }

    @Test
    void targetReturnShiftsWeightTowardTheHigherReturnAsset() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        // Flat-ish, near-zero mean.
        returns.put("FLAT", new double[]{0.001, -0.001, 0.0015, -0.0012, 0.0008, -0.0009});
        // Clear positive drift.
        returns.put("GROWTH", new double[]{0.01, 0.015, 0.008, 0.012, 0.02, 0.011});

        ReturnStatistics stats = ReturnStatistics.from(returns);
        PortfolioOptimizer optimizer = new PortfolioOptimizer(stats);

        OptimizationResult minVariance = optimizer.minimumVariance();
        OptimizationResult highTarget = optimizer.targetReturn(0.015, 5000.0);

        assertTrue(highTarget.getExpectedReturn() > minVariance.getExpectedReturn(),
                "targeting a high return should raise expected return relative to the pure minimum-variance portfolio");
        assertTrue(highTarget.getWeights().get("GROWTH") > minVariance.getWeights().get("GROWTH"),
                "targeting a high return should shift weight toward the higher-return asset");
    }

    @Test
    void nonPositiveReturnPenaltyThrows() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.01, 0.02, -0.01});
        returns.put("B", new double[]{0.02, -0.01, 0.03});
        ReturnStatistics stats = ReturnStatistics.from(returns);
        PortfolioOptimizer optimizer = new PortfolioOptimizer(stats);

        assertThrows(IllegalArgumentException.class, () -> optimizer.targetReturn(0.01, 0.0));
        assertThrows(IllegalArgumentException.class, () -> optimizer.targetReturn(0.01, -1.0));
    }
}
