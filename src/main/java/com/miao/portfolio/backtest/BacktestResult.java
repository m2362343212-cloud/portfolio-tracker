package com.miao.portfolio.backtest;

import java.time.LocalDate;
import java.util.List;

/**
 * What one strategy did, period by period.
 *
 * @param strategy    the strategy's name
 * @param dates       end date of each holding period
 * @param netReturns  portfolio return in each period, after trading costs
 * @param turnovers   one-way turnover at the rebalance that started each period (0.10 = 10% of the portfolio traded)
 * @param weights     target weights set at the start of each period, weights[t][i]
 */
public record BacktestResult(String strategy, List<LocalDate> dates, double[] netReturns,
                             double[] turnovers, double[][] weights) {
}
