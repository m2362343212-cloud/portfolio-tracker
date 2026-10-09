package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplexProjectionTest {

    private static final double DELTA = 1e-9;

    private static double sum(double[] v) {
        double total = 0;
        for (double x : v) total += x;
        return total;
    }

    @Test
    void pointAlreadyOnSimplexIsUnchanged() {
        double[] v = {0.2, 0.5, 0.3};
        double[] projected = SimplexProjection.project(v);

        assertEquals(0.2, projected[0], DELTA);
        assertEquals(0.5, projected[1], DELTA);
        assertEquals(0.3, projected[2], DELTA);
    }

    @Test
    void uniformOverweightPointProjectsToUniformSimplexPoint() {
        // f(w) is symmetric here, so [1,1,1] (sum 3) should project to [1/3,1/3,1/3].
        double[] projected = SimplexProjection.project(new double[]{1, 1, 1});

        assertEquals(1.0 / 3, projected[0], DELTA);
        assertEquals(1.0 / 3, projected[1], DELTA);
        assertEquals(1.0 / 3, projected[2], DELTA);
    }

    @Test
    void singleSpikeProjectsToAllWeightOnThatCoordinate() {
        // Closest point on the simplex to [2,0,0] is [1,0,0] (hand-verified via the KKT conditions).
        double[] projected = SimplexProjection.project(new double[]{2, 0, 0});

        assertEquals(1.0, projected[0], DELTA);
        assertEquals(0.0, projected[1], DELTA);
        assertEquals(0.0, projected[2], DELTA);
    }

    @Test
    void resultAlwaysSumsToOneAndHasNoNegativeEntries() {
        double[][] cases = {
                {-1, 5, 0.2},
                {10, -10, 10},
                {0, 0, 0},
                {-0.5, -0.3, -0.1, -0.9},
                {100, 0.001, -50, 3},
        };

        for (double[] input : cases) {
            double[] projected = SimplexProjection.project(input);
            assertEquals(1.0, sum(projected), 1e-8, "weights must sum to 1 for input " + java.util.Arrays.toString(input));
            for (double w : projected) {
                assertTrue(w >= -1e-12, "no weight may be negative, got " + w + " for input " + java.util.Arrays.toString(input));
            }
        }
    }

    @Test
    void projectionOfAllZerosSpreadsWeightUniformly() {
        double[] projected = SimplexProjection.project(new double[]{0, 0, 0, 0});
        for (double w : projected) {
            assertEquals(0.25, w, DELTA);
        }
    }
}
