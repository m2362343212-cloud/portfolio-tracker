package com.miao.portfolio.optimize;

import java.util.Arrays;

/**
 * Euclidean projection of an arbitrary real vector onto the probability
 * simplex {@code {w : sum(w) = 1, w_i >= 0}} -- i.e. the closest "fully
 * invested, long-only" weight vector to a given point, in the
 * least-squares sense.
 *
 * <p>This is the projection step {@link PortfolioOptimizer} applies after
 * every gradient step: take an unconstrained descent step, then pull the
 * result back onto the simplex, so every iterate -- not just the final
 * one -- is already a valid portfolio (weights sum to 1, none negative).
 * It implements the standard sort-based algorithm (Held, Wolfe &amp;
 * Crowder, 1974; popularized for sparse/simplex-constrained learning by
 * Duchi et al., "Efficient Projections onto the L1-Ball for Learning in
 * High Dimensions," ICML 2008, Figure 1) rather than the common
 * shortcut of "clip negative weights to zero and renormalize," which is
 * <em>not</em> the true Euclidean projection: clipping first and
 * renormalizing after can leave the closest point on the simplex
 * unreached, especially when several weights are near zero.</p>
 */
public final class SimplexProjection {

    private SimplexProjection() {
    }

    public static double[] project(double[] v) {
        int n = v.length;
        double[] sortedDescending = v.clone();
        Arrays.sort(sortedDescending);
        reverse(sortedDescending);

        double cumulativeSum = 0.0;
        double theta = 0.0;
        boolean found = false;
        for (int i = 0; i < n; i++) {
            cumulativeSum += sortedDescending[i];
            double candidateTheta = (cumulativeSum - 1.0) / (i + 1);
            if (sortedDescending[i] - candidateTheta > 0) {
                theta = candidateTheta;
                found = true;
            }
        }
        if (!found) {
            // Degenerate only for a pathological (e.g. NaN-laden) input; falls
            // back to using every coordinate, which is what the loop above
            // would converge to if the strict inequality were relaxed to >=.
            theta = (cumulativeSum - 1.0) / n;
        }

        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = Math.max(v[i] - theta, 0.0);
        }
        return result;
    }

    private static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }
}
