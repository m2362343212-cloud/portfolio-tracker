package com.miao.portfolio.optimize;

/**
 * A minimal general-purpose matrix type supporting the handful of
 * operations {@link BlackLittermanModel}'s Bayesian update needs:
 * multiply, transpose, add, scale, and inverse.
 *
 * <p>{@link PortfolioOptimizer} deliberately avoids ever needing a matrix
 * inverse (see its own Javadoc for why). Black-Litterman can't avoid one:
 * computing a closed-form Gaussian posterior from a Gaussian prior and
 * Gaussian-uncertain views is, mathematically, a matrix-inversion problem
 * (see {@link BlackLittermanModel#combine}). This class exists to support
 * exactly that, implemented from scratch rather than pulling in an
 * external linear-algebra library, consistent with the rest of this
 * project. It is package-private: an implementation detail of the
 * Black-Litterman model, not part of this package's public API.</p>
 */
final class Matrix {

    private final double[][] data;
    private final int rows;
    private final int cols;

    Matrix(double[][] data) {
        this.rows = data.length;
        this.cols = rows == 0 ? 0 : data[0].length;
        this.data = new double[rows][];
        for (int i = 0; i < rows; i++) {
            if (data[i].length != cols) {
                throw new IllegalArgumentException("all rows must have the same length");
            }
            this.data[i] = data[i].clone();
        }
    }

    /** A column vector (n x 1) from a flat array. */
    static Matrix column(double[] values) {
        double[][] data = new double[values.length][1];
        for (int i = 0; i < values.length; i++) {
            data[i][0] = values[i];
        }
        return new Matrix(data);
    }

    static Matrix identity(int n) {
        double[][] data = new double[n][n];
        for (int i = 0; i < n; i++) {
            data[i][i] = 1.0;
        }
        return new Matrix(data);
    }

    int rows() {
        return rows;
    }

    int cols() {
        return cols;
    }

    double get(int r, int c) {
        return data[r][c];
    }

    Matrix transpose() {
        double[][] result = new double[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = data[i][j];
            }
        }
        return new Matrix(result);
    }

    Matrix multiply(Matrix other) {
        if (this.cols != other.rows) {
            throw new IllegalArgumentException(
                    "cannot multiply a %dx%d matrix by a %dx%d matrix".formatted(rows, cols, other.rows, other.cols));
        }
        double[][] result = new double[rows][other.cols];
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < cols; k++) {
                double lhs = data[i][k];
                if (lhs == 0.0) continue;
                for (int j = 0; j < other.cols; j++) {
                    result[i][j] += lhs * other.data[k][j];
                }
            }
        }
        return new Matrix(result);
    }

    Matrix add(Matrix other) {
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("cannot add matrices of different shapes");
        }
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = data[i][j] + other.data[i][j];
            }
        }
        return new Matrix(result);
    }

    Matrix scale(double s) {
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = data[i][j] * s;
            }
        }
        return new Matrix(result);
    }

    /**
     * The matrix inverse, via Gauss-Jordan elimination with partial
     * pivoting (selecting the largest-magnitude candidate pivot in each
     * column) for numerical stability.
     *
     * @throws IllegalStateException if the matrix isn't square, or is
     *         singular (or so close to singular that no usable pivot
     *         remains)
     */
    Matrix inverse() {
        if (rows != cols) {
            throw new IllegalStateException("only a square matrix can be inverted, got %dx%d".formatted(rows, cols));
        }
        int n = rows;
        // Augmented [A | I]; row-reduce the left half to I, and the right
        // half becomes A^-1.
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(data[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            int pivotRow = col;
            double pivotMagnitude = Math.abs(aug[col][col]);
            for (int r = col + 1; r < n; r++) {
                double magnitude = Math.abs(aug[r][col]);
                if (magnitude > pivotMagnitude) {
                    pivotRow = r;
                    pivotMagnitude = magnitude;
                }
            }
            if (pivotMagnitude < 1e-12) {
                throw new IllegalStateException("matrix is singular (or numerically indistinguishable from singular); cannot invert");
            }
            double[] tmp = aug[col];
            aug[col] = aug[pivotRow];
            aug[pivotRow] = tmp;

            double pivot = aug[col][col];
            for (int j = 0; j < 2 * n; j++) {
                aug[col][j] /= pivot;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double factor = aug[r][col];
                if (factor == 0.0) continue;
                for (int j = 0; j < 2 * n; j++) {
                    aug[r][j] -= factor * aug[col][j];
                }
            }
        }

        double[][] result = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, result[i], 0, n);
        }
        return new Matrix(result);
    }

    double[][] toArray() {
        double[][] copy = new double[rows][];
        for (int i = 0; i < rows; i++) {
            copy[i] = data[i].clone();
        }
        return copy;
    }

    /** Flattens a single-column (n x 1) matrix back to a plain array. */
    double[] toColumnArray() {
        if (cols != 1) {
            throw new IllegalStateException("toColumnArray() requires a single-column matrix, got %d columns".formatted(cols));
        }
        double[] result = new double[rows];
        for (int i = 0; i < rows; i++) {
            result[i] = data[i][0];
        }
        return result;
    }
}
