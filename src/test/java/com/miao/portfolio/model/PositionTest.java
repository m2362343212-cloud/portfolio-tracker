package com.miao.portfolio.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionTest {

    private static Trade buy(String symbol, String qty, String price) {
        return new Trade(LocalDate.of(2026, 1, 1), symbol, TradeSide.BUY, new BigDecimal(qty), new BigDecimal(price));
    }

    private static Trade sell(String symbol, String qty, String price) {
        return new Trade(LocalDate.of(2026, 1, 2), symbol, TradeSide.SELL, new BigDecimal(qty), new BigDecimal(price));
    }

    @Test
    void singleBuySetsAverageCostToThatBuysPrice() {
        Position position = new Position("AAPL");
        position.apply(buy("AAPL", "10", "185.20"));

        assertEquals(0, new BigDecimal("10").compareTo(position.getQuantity()));
        assertEquals(0, new BigDecimal("185.20").compareTo(position.getAverageCost()));
    }

    @Test
    void secondBuyBlendsIntoWeightedAverageCost() {
        Position position = new Position("AAPL");
        position.apply(buy("AAPL", "10", "185.20")); // cost basis 1852.00
        position.apply(buy("AAPL", "5", "190.00"));   // cost basis 950.00 -> total 2802.00 / 15 = 186.80

        assertEquals(0, new BigDecimal("15").compareTo(position.getQuantity()));
        assertEquals(0, new BigDecimal("186.8").compareTo(position.getAverageCost()));
    }

    @Test
    void sellRealizesPnlAgainstAverageCostWithoutChangingIt() {
        Position position = new Position("AAPL");
        position.apply(buy("AAPL", "10", "185.20"));
        position.apply(buy("AAPL", "5", "190.00")); // avg cost now 186.80

        position.apply(sell("AAPL", "8", "201.50"));

        // realized = (201.50 - 186.80) * 8 = 117.60
        assertEquals(0, new BigDecimal("117.6").compareTo(position.getRealizedPnl()));
        assertEquals(0, new BigDecimal("7").compareTo(position.getQuantity()));
        // average cost is untouched by a partial sell
        assertEquals(0, new BigDecimal("186.8").compareTo(position.getAverageCost()));
        assertTrue(position.isOpen());
    }

    @Test
    void sellingEntirePositionResetsAverageCostToZero() {
        Position position = new Position("MSFT");
        position.apply(buy("MSFT", "20", "370.00"));
        position.apply(sell("MSFT", "20", "400.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(position.getQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getAverageCost()));
        assertFalse(position.isOpen());
        // realized = (400 - 370) * 20 = 600
        assertEquals(0, new BigDecimal("600").compareTo(position.getRealizedPnl()));
    }

    @Test
    void sellingMoreThanHeldThrowsInsufficientPositionException() {
        Position position = new Position("NVDA");
        position.apply(buy("NVDA", "5", "120.00"));

        InsufficientPositionException ex = assertThrows(InsufficientPositionException.class,
                () -> position.apply(sell("NVDA", "6", "130.00")));
        assertTrue(ex.getMessage().contains("NVDA"));
    }

    @Test
    void applyingATradeForAnotherSymbolThrows() {
        Position position = new Position("AAPL");
        assertThrows(IllegalArgumentException.class, () -> position.apply(buy("MSFT", "1", "1.00")));
    }

    @Test
    void unrealizedPnlReflectsCurrentMarketPrice() {
        Position position = new Position("NVDA");
        position.apply(buy("NVDA", "10", "120.00"));

        // (140 - 120) * 10 = 200
        assertEquals(0, new BigDecimal("200").compareTo(position.unrealizedPnl(new BigDecimal("140.00"))));
    }

    @Test
    void unrealizedPnlIsZeroWhenPositionIsClosed() {
        Position position = new Position("NVDA");
        position.apply(buy("NVDA", "10", "120.00"));
        position.apply(sell("NVDA", "10", "140.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(position.unrealizedPnl(new BigDecimal("999.00"))));
    }

    @Test
    void historyRecordsEveryTradeInOrder() {
        Position position = new Position("AAPL");
        Trade t1 = buy("AAPL", "10", "185.20");
        Trade t2 = sell("AAPL", "4", "200.00");
        position.apply(t1);
        position.apply(t2);

        assertEquals(2, position.getHistory().size());
        assertEquals(t1, position.getHistory().get(0));
        assertEquals(t2, position.getHistory().get(1));
    }
}
