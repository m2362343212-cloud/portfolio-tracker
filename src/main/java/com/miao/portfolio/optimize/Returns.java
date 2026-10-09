package com.miao.portfolio.optimize;

/** Turns a raw price series into simple period-over-period returns. */
public final class Returns {

    private Returns() {
    }

    /**
     * Simple returns r[t] = prices[t+1] / prices[t] - 1, for t = 0 .. n-2.
     * The result has one fewer element than {@code prices}, since a return
     * needs two consecutive prices.
     */
    public static double[] simpleReturns(double[] prices) {
        if (prices.length < 2) {
            throw new IllegalArgumentException("need at least 2 prices to compute a return, got " + prices.length);
        }
        double[] returns = new double[prices.length - 1];
        for (int t = 1; t < prices.length; t++) {
            if (prices[t - 1] == 0.0) {
                throw new IllegalArgumentException("cannot compute a return from a zero price at index " + (t - 1));
            }
            returns[t - 1] = prices[t] / prices[t - 1] - 1.0;
        }
        return returns;
    }
}
