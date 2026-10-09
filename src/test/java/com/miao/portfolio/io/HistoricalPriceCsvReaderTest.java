package com.miao.portfolio.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalPriceCsvReaderTest {

    @Test
    void parsesAlignedSeriesInDateOrder() throws IOException {
        String csv = """
                date,symbol,price
                2026-01-02,AAPL,180.00
                2026-01-02,MSFT,360.00
                2026-01-03,AAPL,182.00
                2026-01-03,MSFT,365.00
                2026-01-04,AAPL,181.50
                2026-01-04,MSFT,363.00
                """;

        Map<String, double[]> result = HistoricalPriceCsvReader.read(new StringReader(csv));

        assertArrayEquals(new double[]{180.00, 182.00, 181.50}, result.get("AAPL"), 1e-9);
        assertArrayEquals(new double[]{360.00, 365.00, 363.00}, result.get("MSFT"), 1e-9);
    }

    @Test
    void datesMissingForAnySymbolAreDroppedForEverySymbol() throws IOException {
        // MSFT has no price on 2026-01-03, so that date must be excluded from
        // BOTH series -- not just left as a gap in MSFT's -- so the two
        // resulting arrays stay the same length and index-aligned.
        String csv = """
                date,symbol,price
                2026-01-02,AAPL,180.00
                2026-01-02,MSFT,360.00
                2026-01-03,AAPL,182.00
                2026-01-04,AAPL,181.50
                2026-01-04,MSFT,363.00
                """;

        Map<String, double[]> result = HistoricalPriceCsvReader.read(new StringReader(csv));

        assertArrayEquals(new double[]{180.00, 181.50}, result.get("AAPL"), 1e-9);
        assertArrayEquals(new double[]{360.00, 363.00}, result.get("MSFT"), 1e-9);
    }

    @Test
    void blankLinesAreIgnored() throws IOException {
        String csv = """
                date,symbol,price
                2026-01-02,AAPL,180.00

                2026-01-03,AAPL,182.00
                """;

        Map<String, double[]> result = HistoricalPriceCsvReader.read(new StringReader(csv));

        assertArrayEquals(new double[]{180.00, 182.00}, result.get("AAPL"), 1e-9);
    }

    @Test
    void wrongColumnCountThrowsWithLineNumber() {
        String csv = """
                date,symbol,price
                2026-01-02,AAPL,180.00
                2026-01-03,AAPL,182.00,extra
                """;

        HistoricalPriceCsvFormatException ex = assertThrows(HistoricalPriceCsvFormatException.class,
                () -> HistoricalPriceCsvReader.read(new StringReader(csv)));
        assertTrue(ex.getMessage().contains("line 3"));
    }

    @Test
    void unparseablePriceThrowsWithLineNumber() {
        String csv = """
                date,symbol,price
                2026-01-02,AAPL,not-a-number
                """;

        HistoricalPriceCsvFormatException ex = assertThrows(HistoricalPriceCsvFormatException.class,
                () -> HistoricalPriceCsvReader.read(new StringReader(csv)));
        assertTrue(ex.getMessage().contains("line 2"));
    }

    @Test
    void symbolsAreUpperCasedAndTrimmed() throws IOException {
        String csv = """
                date,symbol,price
                2026-01-02, aapl ,180.00
                2026-01-03,aapl,182.00
                """;

        Map<String, double[]> result = HistoricalPriceCsvReader.read(new StringReader(csv));

        assertEquals(1, result.size());
        assertArrayEquals(new double[]{180.00, 182.00}, result.get("AAPL"), 1e-9);
    }
}
