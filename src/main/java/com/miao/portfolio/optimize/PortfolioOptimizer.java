package com.miao.portfolio.optimize;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Solves for long-only, fully-invested portfolio weights that minimize
 * variance -- optionally subject to hitting a target expected return --
 * using projected gradient descent.
 *
 * <h2>Why not just invert the covariance matrix?</h2>
 *
 * <p>The classic Markowitz mean-variance problem has a textbook closed-form
 * solution via matrix inversion (the unconstrained minimum-variance weights
 * are proportional to &Sigma;<sup>-1</sup>&middot;<b>1</b>). But that
 * solution routinely assigns <em>negative</em> weight to some instrument --
 * i.e. it wants to short it -- which this tracker deliberately doesn't
 * support (see {@link com.miao.portfolio.model.InsufficientPositionException}
 * and the "long-only" scope note in the project README). Inverting
 * &Sigma; and then clipping negative weights to zero afterward doesn't
 * actually solve the constrained problem -- it solves a different,
 * unconstrained one and then damages the answer.</p>
 *
 * <p>This optimizer instead works directly in the constrained space:
 * every iteration takes a gradient step against the (unconstrained)
 * variance objective, then projects the result back onto the probability
 * simplex with {@link SimplexProjection}. That means every intermediate
 * iterate -- not just the final one -- is already a valid long-only,
 * fully-invested portfolio, and the algorithm converges to the true
 * constrained optimum rather than an approximation of it.</p>
 */
public final class PortfolioOptimizer {

    private static final int MAX_ITERATIONS = 5_000;

    // Convergence is checked on how far one projected gradient step still
    // moves the iterate, not on the raw gradient norm. At a simplex-
    // constrained optimum the *unprojected* gradient generally does not
    // vanish (only its component along the constraint does -- the KKT
    // stationarity condition here is "equal gradient across every
    // strictly-positive weight," not "zero gradient"), so a fixed point of
    // projected gradient descent (next iterate == current iterate) is the
    // correct stopping condition, and this tolerance is on weight-space
    // distance, not gradient magnitude.
    private static final double ITERATE_TOLERANCE = 1e-10;

    // Safety factor applied to the 1/L step-size bound for gradient descent
    // on an L-smooth convex objective; keeping some margin below the
    // theoretical stability limit avoids oscillation from the discrete
    // (non-infinitesimal) step size actually used.
    private static final double STEP_SIZE_SAFETY_FACTOR = 0.5;

    private final ReturnStatistics stats;

    public PortfolioOptimizer(ReturnStatistics stats) {
        this.stats = stats;
    }

    /** The global minimum-variance, long-only, fully-invested portfolio. */
    public OptimizationResult minimumVariance() {
        return optimize(null, 0.0);
    }

    /**
     * The long-only, fully-invested portfolio of (approximately) minimum
     * variance that achieves at least {@code targetReturn} expected return.
     *
     * <p>The target is enforced with a quadratic penalty on the shortfall
     * below it -- a standard technique for folding an inequality constraint
     * into an objective that projected gradient descent can then solve
     * directly against the (separately-enforced) simplex constraint. It is
     * a <em>soft</em> constraint: a larger {@code returnPenalty} pushes the
     * result closer to exactly hitting the target (check
     * {@link OptimizationResult#getExpectedReturn()} against it), at the
     * cost of pulling further from the true minimum-variance point among
     * portfolios that clear the target.</p>
     *
     * @throws IllegalArgumentException if returnPenalty is not positive
     */
    public OptimizationResult targetReturn(double targetReturn, double returnPenalty) {
        if (returnPenalty <= 0) {
            throw new IllegalArgumentException("returnPenalty must be positive, got " + returnPenalty);
        }
        return optimize(targetReturn, returnPenalty);
    }

    private OptimizationResult optimize(Double targetReturn, double returnPenalty) {
        int n = stats.size();
        double[][] covariance = stats.getCovariance();
        double[] meanReturns = stats.getMeanReturns();
        double stepSize = stepSize(covariance, meanReturns, targetReturn, returnPenalty);

        double[] weights = new double[n];
        Arrays.fill(weights, 1.0 / n); // start at the equal-weight portfolio

        int iteration = 0;
        for (; iteration < MAX_ITERATIONS; iteration++) {
            double[] gradient = objectiveGradient(covariance, meanReturns, weights, targetReturn, returnPenalty);

            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                next[i] = weights[i] - stepSize * gradient[i];
            }
            next = SimplexProjection.project(next);

            double movement = distance(next, weights);
            weights = next;
            if (movement < ITERATE_TOLERANCE) {
                iteration++;
                break;
            }
        }

        double variance = quadraticForm(covariance, weights);
        double expectedReturn = dot(meanReturns, weights);

        List<String> symbols = stats.getSymbols();
        Map<String, Double> weightMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            weightMap.put(symbols.get(i), weights[i]);
        }
        return new OptimizationResult(weightMap, expectedReturn, variance, iteration);
    }

    /** Gradient of variance, plus (when targeting a return) the one-sided shortfall-penalty term. */
    private static double[] objectiveGradient(double[][] covariance, double[] meanReturns, double[] weights,
                                               Double targetReturn, double returnPenalty) {
        int n = weights.length;
        double[] gradient = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                sum += covariance[i][j] * weights[j];
            }
            gradient[i] = 2 * sum; // d/dw [w^T Sigma w] = 2 Sigma w
        }

        if (targetReturn != null) {
            double shortfall = targetReturn - dot(meanReturns, weights);
            if (shortfall > 0) {
                // d/dw [ returnPenalty * (targetReturn - w.meanReturns)^2 ] = -2 * returnPenalty * shortfall * meanReturns
                for (int i = 0; i < n; i++) {
                    gradient[i] -= 2 * returnPenalty * shortfall * meanReturns[i];
                }
            }
            // shortfall <= 0 means the target is already met or exceeded: no
            // penalty gradient, so descent is free to keep reducing variance.
        }
        return gradient;
    }

    /**
     * A step size guaranteed to keep gradient descent stable on this
     * L-smooth quadratic (plus, when present, quadratic-penalty) objective:
     * {@code stepSize = STEP_SIZE_SAFETY_FACTOR / L}, where L is an upper
     * bound on the objective's Lipschitz constant. L for {@code w^T Sigma w}
     * is {@code 2 * spectralRadius(Sigma)}, bounded via Gershgorin's circle
     * theorem by the largest absolute row sum of Sigma (valid because
     * Sigma is symmetric) rather than computed exactly, which would need an
     * eigendecomposition this project deliberately doesn't implement.
     */
    private static double stepSize(double[][] covariance, double[] meanReturns, Double targetReturn,
                                    double returnPenalty) {
        double maxAbsRowSum = 0.0;
        for (double[] row : covariance) {
            double rowSum = 0.0;
            for (double value : row) {
                rowSum += Math.abs(value);
            }
            maxAbsRowSum = Math.max(maxAbsRowSum, rowSum);
        }
        double lipschitz = 2 * maxAbsRowSum;

        if (targetReturn != null) {
            double meanNormSquared = 0.0;
            for (double m : meanReturns) {
                meanNormSquared += m * m;
            }
            lipschitz += 2 * returnPenalty * meanNormSquared;
        }

        if (lipschitz <= 0.0) {
            return 0.1; // covariance (and mean, if relevant) are all exactly zero; any small step is fine
        }
        return STEP_SIZE_SAFETY_FACTOR / lipschitz;
    }

    private static double quadraticForm(double[][] matrix, double[] vector) {
        int n = vector.length;
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            double rowSum = 0.0;
            for (int j = 0; j < n; j++) {
                rowSum += matrix[i][j] * vector[j];
            }
            total += vector[i] * rowSum;
        }
        return total;
    }

    private static double dot(double[] a, double[] b) {
        double total = 0.0;
        for (int i = 0; i < a.length; i++) {
            total += a[i] * b[i];
        }
        return total;
    }

    private static double distance(double[] a, double[] b) {
        double sumSquares = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sumSquares += diff * diff;
        }
        return Math.sqrt(sumSquares);
    }
}
