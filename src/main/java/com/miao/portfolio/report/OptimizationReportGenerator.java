package com.miao.portfolio.report;

import com.miao.portfolio.optimize.OptimizationResult;

import java.util.Map;

/**
 * Formats an {@link OptimizationResult} into a human-readable text report:
 * one row per suggested weight plus the resulting portfolio-level expected
 * return and risk.
 */
public final class OptimizationReportGenerator {

    private static final String ROW_FORMAT = "%-8s %10s%n";

    private OptimizationReportGenerator() {
    }

    public static String generate(String title, OptimizationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(78)).append(System.lineSeparator());
        sb.append(title).append(System.lineSeparator());
        sb.append("=".repeat(78)).append(System.lineSeparator());
        sb.append(ROW_FORMAT.formatted("SYMBOL", "WEIGHT"));
        sb.append("-".repeat(78)).append(System.lineSeparator());

        for (Map.Entry<String, Double> entry : result.getWeights().entrySet()) {
            sb.append(ROW_FORMAT.formatted(entry.getKey(), percent(entry.getValue())));
        }

        sb.append("-".repeat(78)).append(System.lineSeparator());
        sb.append("Expected return (per period): %s%n".formatted(percent(result.getExpectedReturn())));
        sb.append("Volatility (per period, std dev): %s%n".formatted(percent(result.getStandardDeviation())));
        sb.append("Converged after %d iterations%n".formatted(result.getIterations()));
        sb.append("=".repeat(78)).append(System.lineSeparator());

        return sb.toString();
    }

    private static String percent(double value) {
        return "%.2f%%".formatted(value * 100);
    }
}
