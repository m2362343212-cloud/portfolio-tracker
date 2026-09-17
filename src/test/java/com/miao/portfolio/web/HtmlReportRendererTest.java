package com.miao.portfolio.web;

import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Trade;
import com.miao.portfolio.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReportRendererTest {

    private static Trade trade(String symbol, TradeSide side, String qty, String price) {
        return new Trade(LocalDate.of(2026, 1, 1), symbol, side, new BigDecimal(qty), new BigDecimal(price));
    }

    @Test
    void rendersOpenPositionWithComputedValues() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("AAPL", TradeSide.BUY, "10", "185.20"));

        String html = HtmlReportRenderer.render(portfolio, Map.of("AAPL", new BigDecimal("205.00")));

        assertTrue(html.contains("AAPL"));
        assertTrue(html.contains("185.20")); // avg cost
        assertTrue(html.contains("205.00")); // last price
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void negativeUnrealizedPnlGetsNegativeCssClass() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("MSFT", TradeSide.BUY, "10", "400.00"));

        String html = HtmlReportRenderer.render(portfolio, Map.of("MSFT", new BigDecimal("350.00")));

        assertTrue(html.contains("class=\"negative\">-500.00"));
    }

    @Test
    void positiveUnrealizedPnlGetsPositiveCssClass() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("NVDA", TradeSide.BUY, "10", "120.00"));

        String html = HtmlReportRenderer.render(portfolio, Map.of("NVDA", new BigDecimal("140.00")));

        assertTrue(html.contains("class=\"positive\">200.00"));
    }

    @Test
    void closedPositionsAppearInTheirOwnSection() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrades(List.of(
                trade("MSFT", TradeSide.BUY, "20", "370.00"),
                trade("MSFT", TradeSide.SELL, "20", "400.00")
        ));

        String html = HtmlReportRenderer.render(portfolio, Map.of());

        assertTrue(html.contains("Closed Positions"));
        assertTrue(html.contains("MSFT"));
        assertFalse(html.contains("no market price supplied")); // MSFT is closed, shouldn't need a price
    }

    @Test
    void missingMarketPriceForOpenPositionIsFlaggedRatherThanCrashing() {
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("TSLA", TradeSide.BUY, "5", "250.00"));

        String html = HtmlReportRenderer.render(portfolio, Map.of());

        assertTrue(html.contains("no market price supplied"));
    }

    @Test
    void symbolIsHtmlEscaped() {
        // Trade normalizes/uppercases symbols but never strips angle brackets outright,
        // so the renderer's own escaping is the real safety net here.
        Portfolio portfolio = new Portfolio();
        portfolio.applyTrade(trade("<script>", TradeSide.BUY, "1", "1.00"));

        String html = HtmlReportRenderer.render(portfolio, Map.of("<SCRIPT>", new BigDecimal("1.00")));

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;SCRIPT&gt;"));
    }
}
