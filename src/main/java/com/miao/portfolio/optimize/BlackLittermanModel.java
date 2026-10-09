package com.miao.portfolio.optimize;

import java.util.List;

/**
 * Bayesian portfolio construction via the Black-Litterman model (He &amp;
 * Litterman, 1999; building on Black &amp; Litterman, 1992): start from the
 * market's own implied equilibrium expected returns as a prior, then
 * update that prior with the investor's own views -- each carrying its
 * own uncertainty -- to produce a posterior expected-return vector and
 * covariance, ready for {@link PortfolioOptimizer} via {@link
 * BlackLittermanResult#toReturnStatistics}.
 *
 * <h2>Why this, rather than just the sample mean?</h2>
 *
 * <p>{@link ReturnStatistics#from} estimates expected returns as a raw
 * historical sample mean. That estimate is notoriously unstable as an
 * input to mean-variance optimization -- a small change in the sample
 * mean can flip the "optimal" portfolio entirely (Michaud, 1989, called
 * this "error maximization," since mean-variance optimization amplifies
 * exactly the input errors that are largest). Black-Litterman's fix is to
 * start somewhere much better-behaved than the sample mean: the expected
 * returns that would make the market-cap-weighted portfolio itself
 * mean-variance optimal (a "reverse optimization" of the CAPM logic), and
 * then move away from that prior only where -- and only as much as -- the
 * investor holds an explicit, confidence-weighted view. This is Bayesian
 * updating in the textbook sense: a Gaussian prior, a Gaussian likelihood
 * (the views), and a closed-form Gaussian posterior.</p>
 *
 * <h2>The math</h2>
 *
 * <p>Given the prior covariance &Sigma;, market weights w, risk aversion
 * &delta;, and uncertainty scaling &tau;:</p>
 * <pre>
 * implied equilibrium returns:  pi = delta * Sigma * w
 * posterior precision:          M  = (tau*Sigma)^-1 + P^T * Omega^-1 * P
 * posterior covariance-of-mean: A  = M^-1
 * posterior mean:               mu = A * [ (tau*Sigma)^-1 * pi + P^T * Omega^-1 * Q ]
 * posterior covariance (total): Sigma_BL = Sigma + A
 * </pre>
 * <p>where P (the "pick matrix"), Q (view returns), and &Omega; (view
 * uncertainty, diagonal) encode the investor's views -- see {@link View}.</p>
 */
public final class BlackLittermanModel {

    private final List<String> symbols;
    private final double[][] covariance;
    private final double[] impliedEquilibriumReturns;
    private final double tau;

    /**
     * @param prior          supplies the covariance this model treats as fixed and known (its mean is not
     *                       used -- the whole point of Black-Litterman is to replace the sample mean)
     * @param marketWeights  the market-cap (or other benchmark) weights, in the same symbol order as
     *                       {@code prior}; must be positive and sum to (approximately) 1
     * @param riskAversion   delta, the market's implied risk-aversion coefficient; see {@link
     *                       #impliedRiskAversion}. Must be positive.
     * @param tau            a small scalar (commonly 0.01-0.05) expressing how uncertain the prior itself
     *                       is, relative to the return-generating process's own variance. Must be positive.
     * @throws IllegalArgumentException if marketWeights' length doesn't match the prior, isn't all-positive,
     *         doesn't sum to ~1, or riskAversion/tau aren't positive
     */
    public BlackLittermanModel(ReturnStatistics prior, double[] marketWeights, double riskAversion, double tau) {
        this.symbols = prior.getSymbols();
        this.covariance = prior.getCovariance();
        int n = symbols.size();

        if (marketWeights.length != n) {
            throw new IllegalArgumentException(
                    "marketWeights length (%d) must match the number of symbols (%d)".formatted(marketWeights.length, n));
        }
        double weightSum = 0.0;
        for (double w : marketWeights) {
            if (w < 0) {
                throw new IllegalArgumentException("marketWeights must all be non-negative, got " + w);
            }
            weightSum += w;
        }
        if (Math.abs(weightSum - 1.0) > 1e-6) {
            throw new IllegalArgumentException("marketWeights must sum to 1.0, got " + weightSum);
        }
        if (riskAversion <= 0) {
            throw new IllegalArgumentException("riskAversion must be positive, got " + riskAversion);
        }
        if (tau <= 0) {
            throw new IllegalArgumentException("tau must be positive, got " + tau);
        }
        this.tau = tau;

        // pi = delta * Sigma * w
        double[] sigmaW = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                sum += covariance[i][j] * marketWeights[j];
            }
            sigmaW[i] = sum;
        }
        this.impliedEquilibriumReturns = new double[n];
        for (int i = 0; i < n; i++) {
            impliedEquilibriumReturns[i] = riskAversion * sigmaW[i];
        }
    }

    /**
     * A common way to calibrate {@code riskAversion}: delta = (expected market excess return) / (market
     * return variance) -- the risk aversion that makes the market portfolio itself the investor's optimal
     * unconstrained mean-variance holding.
     */
    public static double impliedRiskAversion(double expectedMarketExcessReturn, double marketReturnVariance) {
        if (marketReturnVariance <= 0) {
            throw new IllegalArgumentException("marketReturnVariance must be positive, got " + marketReturnVariance);
        }
        return expectedMarketExcessReturn / marketReturnVariance;
    }

    /** The market-implied equilibrium expected return per symbol, before any views are applied. */
    public double[] getImpliedEquilibriumReturns() {
        return impliedEquilibriumReturns.clone();
    }

    public List<String> getSymbols() {
        return symbols;
    }

    /**
     * One investor view: a linear combination of assets ({@code pick}) expected to return {@code
     * viewReturn}, with {@code confidenceVariance} expressing how uncertain that view is (smaller = more
     * confident -- this is a variance, not a 0-1 confidence score). {@link #absolute} and {@link
     * #relative} build the two standard shapes of view; this constructor is for anything else.
     */
    public record View(double[] pick, double viewReturn, double confidenceVariance) {
        public View {
            if (confidenceVariance <= 0) {
                throw new IllegalArgumentException("confidenceVariance must be positive, got " + confidenceVariance);
            }
            pick = pick.clone();
        }

        @Override
        public double[] pick() {
            return pick.clone();
        }
    }

    /** An "absolute" view: asset at {@code symbolIndex} will return {@code viewReturn}. */
    public View absolute(int symbolIndex, double viewReturn, double confidenceVariance) {
        double[] pick = new double[symbols.size()];
        pick[requireIndex(symbolIndex)] = 1.0;
        return new View(pick, viewReturn, confidenceVariance);
    }

    /**
     * A "relative" view: the asset at {@code outperformIndex} will outperform the asset at {@code
     * underperformIndex} by {@code viewReturn} -- the standard way to express a pairwise view without
     * committing to either asset's absolute return.
     */
    public View relative(int outperformIndex, int underperformIndex, double viewReturn, double confidenceVariance) {
        double[] pick = new double[symbols.size()];
        pick[requireIndex(outperformIndex)] = 1.0;
        pick[requireIndex(underperformIndex)] = -1.0;
        return new View(pick, viewReturn, confidenceVariance);
    }

    /**
     * The He &amp; Litterman (1999) default confidence for an absolute view on one symbol: {@code tau *
     * Sigma[i][i]}, i.e. the view is exactly as uncertain as the prior itself already is for that symbol.
     * A caller with a genuine, better-informed confidence level should pass it directly to {@link
     * #absolute} instead; this is a reasonable default when there isn't one.
     */
    public View absoluteWithDefaultConfidence(int symbolIndex, double viewReturn) {
        int i = requireIndex(symbolIndex);
        return absolute(i, viewReturn, tau * covariance[i][i]);
    }

    private int requireIndex(int symbolIndex) {
        if (symbolIndex < 0 || symbolIndex >= symbols.size()) {
            throw new IndexOutOfBoundsException(
                    "symbolIndex %d out of range for %d symbols".formatted(symbolIndex, symbols.size()));
        }
        return symbolIndex;
    }

    /**
     * Combines the market-implied prior with the given views to produce the Black-Litterman posterior.
     *
     * @throws IllegalArgumentException if views is empty, or any view's pick vector length doesn't match
     *         the number of symbols
     */
    public BlackLittermanResult combine(List<View> views) {
        if (views.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one view is required -- with none, the posterior is just the prior "
                            + "(use getImpliedEquilibriumReturns() directly)");
        }
        int n = symbols.size();
        int k = views.size();

        double[][] pickRows = new double[k][];
        double[] viewReturns = new double[k];
        double[][] omega = new double[k][k];
        for (int i = 0; i < k; i++) {
            View v = views.get(i);
            if (v.pick().length != n) {
                throw new IllegalArgumentException(
                        "view %d's pick vector has length %d, expected %d (one entry per symbol)"
                                .formatted(i, v.pick().length, n));
            }
            pickRows[i] = v.pick();
            viewReturns[i] = v.viewReturn();
            omega[i][i] = v.confidenceVariance();
        }

        Matrix sigma = new Matrix(covariance);
        Matrix tauSigmaInv = sigma.scale(tau).inverse();
        Matrix p = new Matrix(pickRows);
        Matrix pTranspose = p.transpose();
        Matrix omegaInv = new Matrix(omega).inverse();
        Matrix q = Matrix.column(viewReturns);
        Matrix pi = Matrix.column(impliedEquilibriumReturns);

        Matrix pTOmegaInv = pTranspose.multiply(omegaInv);

        // posterior precision M = (tau*Sigma)^-1 + P^T * Omega^-1 * P
        Matrix posteriorPrecision = tauSigmaInv.add(pTOmegaInv.multiply(p));
        // posterior covariance-of-the-mean A = M^-1
        Matrix posteriorCovarianceOfMean = posteriorPrecision.inverse();

        // rhs = (tau*Sigma)^-1 * pi + P^T * Omega^-1 * Q
        Matrix rhs = tauSigmaInv.multiply(pi).add(pTOmegaInv.multiply(q));
        // posterior mean mu = A * rhs
        double[] posteriorMean = posteriorCovarianceOfMean.multiply(rhs).toColumnArray();

        double[][] posteriorCovariance = sigma.add(posteriorCovarianceOfMean).toArray();

        return new BlackLittermanResult(symbols, posteriorMean, posteriorCovariance);
    }
}
