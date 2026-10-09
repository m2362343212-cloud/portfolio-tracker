package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatrixTest {

    private static final double DELTA = 1e-9;

    @Test
    void multiplyComputesTheStandardMatrixProduct() {
        // [1 2] [5 6]   [19 22]
        // [3 4] [7 8] = [43 50]
        Matrix a = new Matrix(new double[][]{{1, 2}, {3, 4}});
        Matrix b = new Matrix(new double[][]{{5, 6}, {7, 8}});

        Matrix product = a.multiply(b);

        assertEquals(19, product.get(0, 0), DELTA);
        assertEquals(22, product.get(0, 1), DELTA);
        assertEquals(43, product.get(1, 0), DELTA);
        assertEquals(50, product.get(1, 1), DELTA);
    }

    @Test
    void transposeSwapsRowsAndColumns() {
        Matrix m = new Matrix(new double[][]{{1, 2, 3}, {4, 5, 6}});
        Matrix t = m.transpose();

        assertEquals(3, t.rows());
        assertEquals(2, t.cols());
        assertEquals(1, t.get(0, 0), DELTA);
        assertEquals(4, t.get(0, 1), DELTA);
        assertEquals(3, t.get(2, 0), DELTA);
        assertEquals(6, t.get(2, 1), DELTA);
    }

    @Test
    void addSumsElementwise() {
        Matrix a = new Matrix(new double[][]{{1, 2}, {3, 4}});
        Matrix b = new Matrix(new double[][]{{10, 20}, {30, 40}});
        Matrix sum = a.add(b);

        assertEquals(11, sum.get(0, 0), DELTA);
        assertEquals(44, sum.get(1, 1), DELTA);
    }

    @Test
    void scaleMultipliesEveryElement() {
        Matrix m = new Matrix(new double[][]{{1, -2}, {3, 4}});
        Matrix scaled = m.scale(2.5);

        assertEquals(2.5, scaled.get(0, 0), DELTA);
        assertEquals(-5.0, scaled.get(0, 1), DELTA);
        assertEquals(10.0, scaled.get(1, 1), DELTA);
    }

    @Test
    void inverseOfDiagonalMatrixInvertsEachEntry() {
        Matrix m = new Matrix(new double[][]{{4, 0}, {0, 5}});
        Matrix inv = m.inverse();

        assertEquals(0.25, inv.get(0, 0), DELTA);
        assertEquals(0.0, inv.get(0, 1), DELTA);
        assertEquals(0.0, inv.get(1, 0), DELTA);
        assertEquals(0.2, inv.get(1, 1), DELTA);
    }

    @Test
    void inverseOfGeneral2x2MatrixMatchesTheClosedFormFormula() {
        // For [[a,b],[c,d]], inverse = 1/(ad-bc) * [[d,-b],[-c,a]]
        // a=4, b=7, c=2, d=6 -> det = 24-14 = 10
        Matrix m = new Matrix(new double[][]{{4, 7}, {2, 6}});
        Matrix inv = m.inverse();

        assertEquals(0.6, inv.get(0, 0), DELTA);   // 6/10
        assertEquals(-0.7, inv.get(0, 1), DELTA);  // -7/10
        assertEquals(-0.2, inv.get(1, 0), DELTA);  // -2/10
        assertEquals(0.4, inv.get(1, 1), DELTA);   // 4/10
    }

    @Test
    void matrixTimesItsInverseIsIdentity() {
        Matrix m = new Matrix(new double[][]{{2, 1, 1}, {1, 3, 2}, {1, 0, 0}});
        Matrix product = m.multiply(m.inverse());

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(i == j ? 1.0 : 0.0, product.get(i, j), 1e-9);
            }
        }
    }

    @Test
    void singularMatrixThrowsOnInverse() {
        // Row 2 is a multiple of row 1 -- singular.
        Matrix m = new Matrix(new double[][]{{1, 2}, {2, 4}});
        assertThrows(IllegalStateException.class, m::inverse);
    }

    @Test
    void nonSquareMatrixThrowsOnInverse() {
        Matrix m = new Matrix(new double[][]{{1, 2, 3}, {4, 5, 6}});
        assertThrows(IllegalStateException.class, m::inverse);
    }

    @Test
    void columnAndToColumnArrayRoundTrip() {
        double[] values = {1.5, -2.5, 3.5};
        Matrix column = Matrix.column(values);

        assertEquals(3, column.rows());
        assertEquals(1, column.cols());
        assertEquals(-2.5, column.get(1, 0), DELTA);

        double[] roundTripped = column.toColumnArray();
        assertEquals(3, roundTripped.length);
        assertEquals(1.5, roundTripped[0], DELTA);
        assertEquals(3.5, roundTripped[2], DELTA);
    }

    @Test
    void toColumnArrayRejectsMultiColumnMatrix() {
        Matrix m = new Matrix(new double[][]{{1, 2}, {3, 4}});
        assertThrows(IllegalStateException.class, m::toColumnArray);
    }

    @Test
    void identityIsMultiplicativeIdentity() {
        Matrix m = new Matrix(new double[][]{{1, 2}, {3, 4}});
        Matrix product = m.multiply(Matrix.identity(2));

        assertEquals(1, product.get(0, 0), DELTA);
        assertEquals(4, product.get(1, 1), DELTA);
    }
}
