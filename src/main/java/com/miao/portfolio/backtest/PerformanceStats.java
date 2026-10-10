package com.miao.portfolio.backtest;

/**
 * The usual summary numbers for a return series.
 *
 * @param annualReturn     compound annual growth rate
 * @param annualVolatility standard deviation of returns, scaled to a year
 * @param sharpe           (annual return - risk-free rate) / annual volatility
 * @param maxDrawdown      worst peak-to-trough fall in portfolio value, as a positive fraction
 * @param annualTurnover   average fraction of the portfolio traded per year (initial purchase excluded)
 */
public record PerformanceStats(double annualReturn, double annualVolatility, double sharpe,
                               double maxDrawdown, double annualTurnover) {

    public static PerformanceStats of(BacktestResult result, int periodsPerYear, double annualRiskFreeRate) {
        double[] r = result.netReturns();
        int n = r.length;
        double growth = 1, peak = 1, maxDd = 0, sum = 0;
        for (double x : r) {
            growth *= 1 + x;
            peak = Math.max(peak, growth);
            maxDd = Math.max(maxDd, 1 - growth / peak);
            sum += x;
        }
        double years = (double) n / periodsPerYear;
        double cagr = Math.pow(growth, 1 / years) - 1;
        double mean = sum / n;
        double ss = 0;
        for (double x : r) {
            ss += (x - mean) * (x - mean);
        }
        double vol = n > 1 ? Math.sqrt(ss / (n - 1)) * Math.sqrt(periodsPerYear) : 0;
        double sharpe = vol > 0 ? (cagr - annualRiskFreeRate) / vol : Double.NaN;
        double turnoverSum = 0;
        for (int k = 1; k < n; k++) {
            turnoverSum += result.turnovers()[k];
        }
        double annualTurnover = n > 1 ? turnoverSum / ((n - 1.0) / periodsPerYear) : 0;
        return new PerformanceStats(cagr, vol, sharpe, maxDd, annualTurnover);
    }
}
