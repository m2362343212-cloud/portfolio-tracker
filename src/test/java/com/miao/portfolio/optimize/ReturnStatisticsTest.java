package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReturnStatisticsTest {

    @Test
    void meanAndCovarianceMatchHandComputedValues() {
        // A: [0.01, 0.03, -0.02, 0.02] -> mean 0.01
        // B: [0.02, 0.01, 0.00, 0.03]  -> mean 0.015
        // Deviations:
        //   A: 0, 0.02, -0.03, 0.01
        //   B: 0.005, -0.005, -0.015, 0.015
        // sample covariance uses an (n-1) = 3 denominator.
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.01, 0.03, -0.02, 0.02});
        returns.put("B", new double[]{0.02, 0.01, 0.00, 0.03});

        ReturnStatistics stats = ReturnStatistics.from(returns);

        assertEquals(java.util.List.of("A", "B"), stats.getSymbols());

        double[] mean = stats.getMeanReturns();
        assertEquals(0.01, mean[0], 1e-9);
        assertEquals(0.015, mean[1], 1e-9);

        double[][] cov = stats.getCovariance();
        // Var(A) = (0^2 + 0.02^2 + (-0.03)^2 + 0.01^2) / 3 = 0.0014 / 3
        assertEquals(0.0014 / 3, cov[0][0], 1e-9);
        // Var(B) = (0.005^2 + (-0.005)^2 + (-0.015)^2 + 0.015^2) / 3 = 0.0005 / 3
        assertEquals(0.0005 / 3, cov[1][1], 1e-9);
        // Cov(A,B) = (0*0.005 + 0.02*-0.005 + -0.03*-0.015 + 0.01*0.015) / 3 = 0.0005 / 3
        assertEquals(0.0005 / 3, cov[0][1], 1e-9);
        assertEquals(cov[0][1], cov[1][0], 1e-12, "covariance matrix must be symmetric");
    }

    @Test
    void symbolOrderMatchesInputMapIterationOrder() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("NVDA", new double[]{0.01, 0.02});
        returns.put("AAPL", new double[]{0.03, -0.01});
        returns.put("MSFT", new double[]{0.00, 0.01});

        ReturnStatistics stats = ReturnStatistics.from(returns);

        assertEquals(java.util.List.of("NVDA", "AAPL", "MSFT"), stats.getSymbols());
        assertEquals(3, stats.size());
    }

    @Test
    void emptyInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> ReturnStatistics.from(Map.of()));
    }

    @Test
    void fewerThanTwoPeriodsThrows() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.01});
        assertThrows(IllegalArgumentException.class, () -> ReturnStatistics.from(returns));
    }

    @Test
    void mismatchedSeriesLengthsThrows() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.01, 0.02, 0.03});
        returns.put("B", new double[]{0.01, 0.02});
        assertThrows(IllegalArgumentException.class, () -> ReturnStatistics.from(returns));
    }

    @Test
    void gettersReturnDefensiveCopies() {
        Map<String, double[]> returns = new LinkedHashMap<>();
        returns.put("A", new double[]{0.01, 0.02, 0.03});
        returns.put("B", new double[]{0.02, 0.00, -0.01});
        ReturnStatistics stats = ReturnStatistics.from(returns);

        double[] mean = stats.getMeanReturns();
        mean[0] = 999.0;
        double[][] cov = stats.getCovariance();
        cov[0][0] = 999.0;

        assertEquals(0.02, stats.getMeanReturns()[0], 1e-9, "mutating a returned array must not affect internal state");
        assertEquals(false, stats.getCovariance()[0][0] == 999.0);
    }
}
