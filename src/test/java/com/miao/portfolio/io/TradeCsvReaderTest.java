package com.miao.portfolio.io;

import com.miao.portfolio.model.Trade;
import com.miao.portfolio.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeCsvReaderTest {

    @Test
    void parsesValidRowsAndSkipsTheHeader() throws IOException {
        String csv = """
                date,symbol,side,quantity,price
                2026-01-05,AAPL,BUY,10,185.20
                2026-02-15,AAPL,SELL,8,201.50
                """;

        List<Trade> trades = TradeCsvReader.read(new StringReader(csv));

        assertEquals(2, trades.size());
        assertEquals(new Trade(LocalDate.of(2026, 1, 5), "AAPL", TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("185.20")), trades.get(0));
        assertEquals(TradeSide.SELL, trades.get(1).getSide());
    }

    @Test
    void blankLinesAreIgnored() throws IOException {
        String csv = """
                date,symbol,side,quantity,price
                2026-01-05,AAPL,BUY,10,185.20

                2026-02-15,AAPL,SELL,8,201.50
                """;

        List<Trade> trades = TradeCsvReader.read(new StringReader(csv));
        assertEquals(2, trades.size());
    }

    @Test
    void wrongColumnCountReportsTheLineNumber() {
        String csv = """
                date,symbol,side,quantity,price
                2026-01-05,AAPL,BUY,10
                """;

        TradeCsvFormatException ex = assertThrows(TradeCsvFormatException.class,
                () -> TradeCsvReader.read(new StringReader(csv)));
        assertTrue(ex.getMessage().contains("line 2"));
    }

    @Test
    void unrecognizedSideReportsTheLineNumber() {
        String csv = """
                date,symbol,side,quantity,price
                2026-01-05,AAPL,HOLD,10,185.20
                """;

        TradeCsvFormatException ex = assertThrows(TradeCsvFormatException.class,
                () -> TradeCsvReader.read(new StringReader(csv)));
        assertTrue(ex.getMessage().contains("line 2"));
    }

    @Test
    void malformedNumberReportsTheLineNumber() {
        String csv = """
                date,symbol,side,quantity,price
                2026-01-05,AAPL,BUY,ten,185.20
                """;

        assertThrows(TradeCsvFormatException.class, () -> TradeCsvReader.read(new StringReader(csv)));
    }
}
