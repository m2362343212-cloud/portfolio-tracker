package com.miao.portfolio.backtest;

import java.util.List;

/**
 * A rule that decides portfolio weights at a rebalancing date, using only
 * returns observed before that date.
 */
public interface AllocationStrategy {

    String name();

    /**
     * @param symbols     the assets, in column order
     * @param pastReturns past periodic returns, pastReturns[t][i], oldest first; never includes the future
     * @return weights in the same order as {@code symbols}, non-negative and summing to 1
     */
    double[] weights(List<String> symbols, double[][] pastReturns);
}
