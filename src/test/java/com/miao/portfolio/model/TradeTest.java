package com.miao.portfolio.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeTest {

    @Test
    void symbolIsTrimmedAndUppercased() {
        Trade trade = new Trade(LocalDate.of(2026, 1, 1), "  aapl ", TradeSide.BUY,
                BigDecimal.TEN, new BigDecimal("100"));
        assertEquals("AAPL", trade.getSymbol());
    }

    @Test
    void zeroOrNegativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Trade(
                LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY, BigDecimal.ZERO, new BigDecimal("100")));
        assertThrows(IllegalArgumentException.class, () -> new Trade(
                LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY, new BigDecimal("-5"), new BigDecimal("100")));
    }

    @Test
    void zeroOrNegativePriceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Trade(
                LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY, BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    void blankSymbolIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Trade(
                LocalDate.of(2026, 1, 1), "   ", TradeSide.BUY, BigDecimal.TEN, new BigDecimal("100")));
    }

    @Test
    void equalsIgnoresTrailingZerosInNumericFields() {
        Trade a = new Trade(LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("100.00"));
        Trade b = new Trade(LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY,
                new BigDecimal("10.0"), new BigDecimal("100"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void grossAmountIsPriceTimesQuantity() {
        Trade trade = new Trade(LocalDate.of(2026, 1, 1), "AAPL", TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("185.20"));
        assertEquals(0, new BigDecimal("1852.00").compareTo(trade.grossAmount()));
    }
}
