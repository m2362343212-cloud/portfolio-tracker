package com.miao.portfolio.backtest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestTest {

    // Two assets, five month-ends. Returns: A = +10%, -10%, +10%, +10%; B = 0%, +5%, 0%, +5%.
    private static final String TINY = """
            date,symbol,price
            2020-01-28,A,100
            2020-01-28,B,100
            2020-02-28,A,110
            2020-02-28,B,100
            2020-03-28,A,99
            2020-03-28,B,105
            2020-04-28,A,108.9
            2020-04-28,B,105
            2020-05-28,A,119.79
            2020-05-28,B,110.25
            """;

    private static PriceTable tiny() throws Exception {
        return PriceTable.read(new StringReader(TINY));
    }

    @Test
    void returnsAreComputedBetweenConsecutiveDates() throws Exception {
        double[][] r = tiny().returns();
        assertEquals(4, r.length);
        assertEquals(0.10, r[0][0], 1e-12);
        assertEquals(-0.10, r[1][0], 1e-12);
        assertEquals(0.05, r[1][1], 1e-12);
    }

    @Test
    void onlyDatesWhereEverySymbolHasAPriceAreKept() throws Exception {
        PriceTable t = PriceTable.read(new StringReader("""
                date,symbol,price
                2020-01-02,A,1
                2020-01-02,B,1
                2020-01-03,A,2
                2020-01-06,A,3
                2020-01-06,B,3
                """));
        assertEquals(List.of(LocalDate.of(2020, 1, 2), LocalDate.of(2020, 1, 6)), t.getDates());
    }

    @Test
    void monthEndsKeepsTheLastDateOfEachMonth() throws Exception {
        PriceTable t = PriceTable.read(new StringReader("""
                date,symbol,price
                2020-01-30,A,1
                2020-01-30,B,1
                2020-01-31,A,2
                2020-01-31,B,2
                2020-02-03,A,3
                2020-02-03,B,3
                """)).monthEnds();
        assertEquals(List.of(LocalDate.of(2020, 1, 31), LocalDate.of(2020, 2, 3)), t.getDates());
    }

    @Test
    void equalWeightGrowthMatchesHandCalculation() throws Exception {
        BacktestResult r = new Backtester(2, 0).run(tiny(), Strategies.equalWeight());
        // invests in returns 3 and 4: 0.5*10% + 0.5*0% = 5%, then 0.5*10% + 0.5*5% = 7.5%
        assertEquals(0.05, r.netReturns()[0], 1e-12);
        assertEquals(0.075, r.netReturns()[1], 1e-12);
        assertEquals(LocalDate.of(2020, 4, 28), r.dates().get(0));
    }

    @Test
    void strategyNeverSeesTheReturnItIsAboutToEarn() throws Exception {
        PriceTable table = tiny();
        double[][] all = table.returns();
        List<double[][]> seen = new ArrayList<>();
        AllocationStrategy spy = new AllocationStrategy() {
            public String name() {
                return "spy";
            }

            public double[] weights(List<String> symbols, double[][] past) {
                seen.add(past);
                return new double[]{0.5, 0.5};
            }
        };
        new Backtester(2, 0).run(table, spy);
        // first decision invests in return 2, so it may only see returns 0 and 1
        assertEquals(2, seen.get(0).length);
        assertEquals(all[1][0], seen.get(0)[1][0], 1e-12);
        assertEquals(all[2][0], seen.get(1)[1][0], 1e-12);
    }

    @Test
    void tradingCostsReduceReturns() throws Exception {
        BacktestResult free = new Backtester(2, 0).run(tiny(), Strategies.equalWeight());
        BacktestResult costly = new Backtester(2, 100).run(tiny(), Strategies.equalWeight());
        // first period buys the whole portfolio: 1% cost on 100% traded
        assertEquals(0.99 * 1.05 - 1, costly.netReturns()[0], 1e-12);
        assertTrue(costly.netReturns()[1] < free.netReturns()[1]);
    }

    @Test
    void inverseVolatilityGivesTheCalmerAssetMoreWeight() throws Exception {
        double[] w = Strategies.inverseVolatility().weights(List.of("A", "B"), new double[][]{{0.10, 0.0}, {-0.10, 0.05}});
        // vol(A) is 4x vol(B), so weights are 1:4
        assertEquals(0.2, w[0], 1e-9);
        assertEquals(0.8, w[1], 1e-9);
    }

    @Test
    void minimumVarianceWeightsAreLongOnlyAndFullyInvested() throws Exception {
        double[][] past = {{0.02, 0.01, -0.01}, {-0.03, 0.00, 0.02}, {0.01, 0.02, 0.00}, {0.04, -0.01, 0.01}};
        double[] w = Strategies.minimumVariance().weights(List.of("A", "B", "C"), past);
        double sum = 0;
        for (double x : w) {
            assertTrue(x >= -1e-9);
            sum += x;
        }
        assertEquals(1.0, sum, 1e-6);
    }

    @Test
    void fixedWeightsMustSumToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> Strategies.fixedWeights("bad", Map.of("A", 0.6, "B", 0.6)));
    }

    @Test
    void maxDrawdownIsTheWorstPeakToTroughFall() {
        BacktestResult r = new BacktestResult("x", List.of(), new double[]{0.10, -0.50, 0.20},
                new double[]{1, 0, 0}, new double[0][]);
        assertEquals(0.50, PerformanceStats.of(r, 12, 0).maxDrawdown(), 1e-12);
    }

    @Test
    void annualReturnCompoundsMonthlyReturns() {
        double[] monthly = new double[12];
        java.util.Arrays.fill(monthly, 0.01);
        BacktestResult r = new BacktestResult("x", List.of(), monthly, new double[12], new double[0][]);
        assertEquals(Math.pow(1.01, 12) - 1, PerformanceStats.of(r, 12, 0).annualReturn(), 1e-12);
    }
}
