package com.miao.portfolio.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTest {

    private static Trade trade(String symbol, TradeSide side, String qty, String price) {
        return new Trade(LocalDate.of(2026, 1, 1), symbol, side, new BigDecimal(qty), new BigDecimal(price));
    }

    @Test
    void applyTradeCreatesAPositionOnFirstSight() {
        Portfolio portfolio = new Portfolio();
        assertNull(portfolio.getPosition("AAPL"));

        portfolio.applyTrade(trade("AAPL", TradeSide.BUY, "10", "185.20"));

        assertEquals("AAPL", portfolio.getPosition("AAPL").getSymbol());
    }

    @Test
    void getPositionIsCaseInsensitive() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("AAPL", TradeSide.BUY, "10", "185.20"));

        assertEquals(0, new BigDecimal("10").compareTo(portfolio.getPosition("aapl").getQuantity()));
    }

    @Test
    void openPositionsExcludesFullyClosedOnes() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(List.of(
                trade("AAPL", TradeSide.BUY, "10", "185.20"),
                trade("MSFT", TradeSide.BUY, "20", "370.00"),
                trade("MSFT", TradeSide.SELL, "20", "400.00") // fully closes MSFT
        ));

        assertEquals(1, portfolio.getOpenPositions().size());
        assertEquals("AAPL", portfolio.getOpenPositions().iterator().next().getSymbol());
        // but the closed position is still visible in the full history
        assertEquals(2, portfolio.getAllPositions().size());
    }

    @Test
    void totalRealizedPnlSumsAcrossAllSymbols() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(List.of(
                trade("AAPL", TradeSide.BUY, "10", "100.00"),
                trade("AAPL", TradeSide.SELL, "10", "110.00"),  // +100
                trade("MSFT", TradeSide.BUY, "5", "200.00"),
                trade("MSFT", TradeSide.SELL, "5", "190.00")    // -50
        ));

        assertEquals(0, new BigDecimal("50").compareTo(portfolio.totalRealizedPnl()));
    }

    @Test
    void totalUnrealizedPnlUsesSuppliedMarketPrices() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(List.of(
                trade("AAPL", TradeSide.BUY, "10", "100.00"),
                trade("MSFT", TradeSide.BUY, "5", "200.00")
        ));

        Map<String, BigDecimal> prices = Map.of("AAPL", new BigDecimal("110.00"), "MSFT", new BigDecimal("190.00"));

        // AAPL: (110-100)*10 = 100 ; MSFT: (190-200)*5 = -50 ; total = 50
        assertEquals(0, new BigDecimal("50").compareTo(portfolio.totalUnrealizedPnl(prices)));
    }

    @Test
    void totalUnrealizedPnlThrowsWhenAnOpenPositionHasNoPrice() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("AAPL", TradeSide.BUY, "10", "100.00"));

        assertTrue(assertThrows(IllegalStateException.class,
                () -> portfolio.totalUnrealizedPnl(Map.of())).getMessage().contains("AAPL"));
    }
}
