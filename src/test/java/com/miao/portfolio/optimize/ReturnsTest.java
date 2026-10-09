package com.miao.portfolio.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReturnsTest {

    @Test
    void computesSimpleReturnsBetweenConsecutivePrices() {
        double[] returns = Returns.simpleReturns(new double[]{100, 110, 99});

        assertEquals(2, returns.length);
        assertEquals(0.10, returns[0], 1e-9); // 110/100 - 1
        assertEquals(-0.10, returns[1], 1e-9); // 99/110 - 1 = 0.9 - 1
    }

    @Test
    void fewerThanTwoPricesThrows() {
        assertThrows(IllegalArgumentException.class, () -> Returns.simpleReturns(new double[]{100}));
        assertThrows(IllegalArgumentException.class, () -> Returns.simpleReturns(new double[]{}));
    }

    @Test
    void zeroPriceThrowsRatherThanProducingInfinityOrNaN() {
        assertThrows(IllegalArgumentException.class, () -> Returns.simpleReturns(new double[]{0, 100}));
    }

    @Test
    void flatPricesProduceZeroReturns() {
        double[] returns = Returns.simpleReturns(new double[]{50, 50, 50});
        assertEquals(0.0, returns[0], 1e-9);
        assertEquals(0.0, returns[1], 1e-9);
    }
}
