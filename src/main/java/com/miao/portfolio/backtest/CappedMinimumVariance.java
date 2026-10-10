package com.miao.portfolio.backtest;

import java.util.List;

/**
 * Minimum-variance weights with a ceiling on every asset class, e.g. no more
 * than 40% in any one. Institutional investors set similar policy ranges so
 * that a portfolio cannot end up concentrated in whatever looked calmest
 * recently.
 *
 * <p>Solved by projected gradient descent on w'Σw, projecting each step onto
 * the "capped simplex" {w : 0 <= w_i <= cap, sum w_i = 1}. The step size is
 * 1/L with L a Gershgorin bound on the largest eigenvalue of 2Σ, so it
 * converges without tuning.</p>
 */
public final class CappedMinimumVariance implements AllocationStrategy {

    private final double cap;

    public CappedMinimumVariance(double cap) {
        if (!(cap > 0 && cap <= 1)) {
            throw new IllegalArgumentException("cap must be in (0, 1], got " + cap);
        }
        this.cap = cap;
    }

    @Override
    public String name() {
        return String.format("Min variance (cap %.0f%%)", cap * 100);
    }

    @Override
    public double[] weights(List<String> symbols, double[][] pastReturns) {
        int n = symbols.size();
        if (cap * n < 1 - 1e-12) {
            throw new IllegalArgumentException("a cap of " + cap + " cannot fully invest " + n + " assets");
        }
        double[][] cov = Strategies.statistics(symbols, pastReturns).getCovariance();
        double lipschitz = 0;
        for (double[] row : cov) {
            double s = 0;
            for (double x : row) {
                s += Math.abs(x);
            }
            lipschitz = Math.max(lipschitz, 2 * s);
        }
        double step = lipschitz > 0 ? 1 / lipschitz : 1;

        double[] w = new double[n];
        java.util.Arrays.fill(w, 1.0 / n);
        for (int iter = 0; iter < 20_000; iter++) {
            double[] v = new double[n];
            for (int i = 0; i < n; i++) {
                double grad = 0;
                for (int j = 0; j < n; j++) {
                    grad += 2 * cov[i][j] * w[j];
                }
                v[i] = w[i] - step * grad;
            }
            double[] next = projectCappedSimplex(v, cap);
            double change = 0;
            for (int i = 0; i < n; i++) {
                change = Math.max(change, Math.abs(next[i] - w[i]));
            }
            w = next;
            if (change < 1e-12) {
                break;
            }
        }
        return w;
    }

    /**
     * Closest point (in Euclidean distance) to v with 0 <= w_i <= cap and sum w_i = 1.
     * The answer has the form w_i = clip(v_i - tau, 0, cap); tau is found by bisection.
     */
    static double[] projectCappedSimplex(double[] v, double cap) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double x : v) {
            lo = Math.min(lo, x - cap);
            hi = Math.max(hi, x);
        }
        for (int k = 0; k < 200; k++) { // sum is non-increasing in tau
            double tau = (lo + hi) / 2;
            if (clippedSum(v, tau, cap) > 1) {
                lo = tau;
            } else {
                hi = tau;
            }
        }
        double tau = (lo + hi) / 2;
        double[] w = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            w[i] = Math.min(cap, Math.max(0, v[i] - tau));
        }
        return w;
    }

    private static double clippedSum(double[] v, double tau, double cap) {
        double s = 0;
        for (double x : v) {
            s += Math.min(cap, Math.max(0, x - tau));
        }
        return s;
    }
}
