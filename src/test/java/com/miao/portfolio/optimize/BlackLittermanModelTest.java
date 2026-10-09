package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackLittermanModelTest {

    private static final double DELTA = 1e-9;

    @Test
    void impliedEquilibriumReturnsMatchHandComputation() {
        // pi = delta * Sigma * w, with Sigma = diag(0.04, 0.09), w = [0.6, 0.4], delta = 2.5
        // pi_A = 2.5 * 0.04 * 0.6 = 0.06 ; pi_B = 2.5 * 0.09 * 0.4 = 0.09
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);

        double[] pi = model.getImpliedEquilibriumReturns();
        assertEquals(0.06, pi[0], DELTA);
        assertEquals(0.09, pi[1], DELTA);
    }

    @Test
    void singleAssetViewPullsPosteriorTowardTheViewByThePrecisionWeightedAverage() {
        // Full hand-derivation (see the conversation/README): with Sigma=diag(0.04,0.09), w=[0.6,0.4],
        // delta=2.5, tau=0.05: pi=[0.06,0.09]. A single view "A returns 8%" with confidence variance
        // 0.001 gives posterior mean A = (500*0.06 + 1000*0.08) / (500+1000) = 0.07333..., where 500 and
        // 1000 are the prior's and the view's precisions (1/(tau*Var(A)) and 1/confidenceVariance).
        // Because Sigma is diagonal (A and B are uncorrelated) and the view touches only A, B's posterior
        // must equal its prior EXACTLY -- a view can't "leak" across uncorrelated assets.
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);

        BlackLittermanModel.View viewOnA = model.absolute(0, 0.08, 0.001);
        BlackLittermanResult result = model.combine(List.of(viewOnA));

        double[] posterior = result.getPosteriorReturnsArray();
        assertEquals(110.0 / 1500.0, posterior[0], 1e-9); // = 0.07333...
        assertEquals(0.09, posterior[1], 1e-9, "B's posterior must be untouched: no correlation, no view on B");

        // Precision-weighted-average sanity check, computed independently of the implementation:
        double priorPrecisionA = 1.0 / (0.05 * 0.04); // 1/(tau*Var(A)) = 500
        double viewPrecisionA = 1.0 / 0.001;            // 1000
        double expected = (priorPrecisionA * 0.06 + viewPrecisionA * 0.08) / (priorPrecisionA + viewPrecisionA);
        assertEquals(expected, posterior[0], 1e-9);
    }

    @Test
    void posteriorCovarianceOfUntouchedAssetEqualsItsPriorTauScaledVariance() {
        // A's posterior variance-of-the-mean should shrink (more information came in); B's should be
        // exactly tau*Var(B), since B received no information at all.
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);
        BlackLittermanResult result = model.combine(List.of(model.absolute(0, 0.08, 0.001)));

        double[][] posteriorCov = result.getPosteriorCovariance();
        // Sigma_BL = Sigma + posteriorCovarianceOfMean; posteriorCovarianceOfMean[B][B] = tau*Var(B) = 0.05*0.09 = 0.0045
        assertEquals(0.09 + 0.0045, posteriorCov[1][1], 1e-9);
        assertEquals(0.04 + 1.0 / 1500.0, posteriorCov[0][0], 1e-9);
        assertEquals(0.0, posteriorCov[0][1], 1e-9, "no correlation was introduced between uncorrelated assets");
    }

    @Test
    void relativeViewIsExpressedAsALongShortPickVector() {
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);

        BlackLittermanModel.View view = model.relative(0, 1, 0.02, 0.001);
        assertEquals(1.0, view.pick()[0], DELTA);
        assertEquals(-1.0, view.pick()[1], DELTA);
        assertEquals(0.02, view.viewReturn(), DELTA);
    }

    @Test
    void defaultConfidenceViewUsesTauTimesThatSymbolsVariance() {
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);

        BlackLittermanModel.View view = model.absoluteWithDefaultConfidence(1, 0.10);
        assertEquals(0.05 * 0.09, view.confidenceVariance(), DELTA);
    }

    @Test
    void resultConvertsToReturnStatisticsUsablePortfolioOptimizerCanConsume() {
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);
        BlackLittermanResult result = model.combine(List.of(model.absolute(0, 0.08, 0.001)));

        ReturnStatistics posteriorStats = result.toReturnStatistics();
        OptimizationResult optimized = new PortfolioOptimizer(posteriorStats).minimumVariance();

        double sum = optimized.getWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6);
        optimized.getWeights().forEach((symbol, weight) -> assertTrue(weight >= -1e-9));
    }

    @Test
    void emptyViewsListThrows() {
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);
        assertThrows(IllegalArgumentException.class, () -> model.combine(List.of()));
    }

    @Test
    void marketWeightsNotSummingToOneThrows() {
        ReturnStatistics prior = handComputableDiagonalStats();
        assertThrows(IllegalArgumentException.class,
                () -> new BlackLittermanModel(prior, new double[]{0.6, 0.6}, 2.5, 0.05));
    }

    @Test
    void nonPositiveRiskAversionOrTauThrows() {
        ReturnStatistics prior = handComputableDiagonalStats();
        assertThrows(IllegalArgumentException.class,
                () -> new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 0.0, 0.05));
        assertThrows(IllegalArgumentException.class,
                () -> new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.0));
    }

    @Test
    void nonPositiveConfidenceVarianceThrows() {
        ReturnStatistics prior = handComputableDiagonalStats();
        BlackLittermanModel model = new BlackLittermanModel(prior, new double[]{0.6, 0.4}, 2.5, 0.05);
        assertThrows(IllegalArgumentException.class, () -> model.absolute(0, 0.08, 0.0));
    }

    /**
     * A prior with EXACTLY Var(A)=0.04, Var(B)=0.09, Cov(A,B)=0, built via {@link
     * ReturnStatistics#of} so the tests above can be verified by hand without also depending on
     * {@link ReturnStatistics#from}'s sample-covariance arithmetic being exactly right (that's
     * ReturnStatisticsTest's job).
     */
    private static ReturnStatistics handComputableDiagonalStats() {
        return ReturnStatistics.of(List.of("A", "B"), new double[]{0.0, 0.0}, new double[][]{{0.04, 0.0}, {0.0, 0.09}});
    }
}
