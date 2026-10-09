package com.miao.portfolio.web;

import com.miao.portfolio.io.TradeCsvReader;
import com.miao.portfolio.model.Portfolio;
import com.miao.portfolio.model.Trade;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local web dashboard on the JDK's built-in HTTP server.
 *
 * Routes:
 *   GET  /               the JavaScript dashboard (src/main/resources/web/index.html)
 *   GET  /report         the server-rendered HTML report, including any --optimize / --bayesian results
 *   GET  /api/portfolio  current portfolio as JSON
 *   POST /api/trades     body = trade CSV text; replaces the trade history, returns new JSON
 */
public final class PortfolioWebServer {

    /** Uploads larger than this are rejected (stops a huge body from exhausting memory). */
    static final int MAX_UPLOAD_BYTES = 1_000_000;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Portfolio> current;
    private final Map<String, BigDecimal> marketPrices;
    private final List<HtmlReportRenderer.ReturnsTable> returnsTables;
    private final List<HtmlReportRenderer.WeightTable> weightTables;

    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices, int port) throws IOException {
        this(portfolio, marketPrices, List.of(), List.of(), port);
    }

    /**
     * @param returnsTables Black-Litterman prior-vs-posterior expected-return tables, shown on /report (from --bayesian); empty if none.
     * @param weightTables   suggested-weights tables, shown on /report (from --optimize and/or --bayesian); empty if none.
     */
    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices,
                              List<HtmlReportRenderer.ReturnsTable> returnsTables,
                              List<HtmlReportRenderer.WeightTable> weightTables,
                              int port) throws IOException {
        this.current = new AtomicReference<>(portfolio);
        this.marketPrices = marketPrices;
        this.returnsTables = returnsTables;
        this.weightTables = weightTables;
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", this::handlePage);
        server.createContext("/report", this::handleReport);
        server.createContext("/api/portfolio", this::handleGetPortfolio);
        server.createContext("/api/trades", this::handlePostTrades);
        server.setExecutor(executor);
    }

    private void handlePage(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }
        try (InputStream in = PortfolioWebServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                send(ex, 500, "text/plain", "index.html missing from resources");
                return;
            }
            send(ex, 200, "text/html", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void handleReport(HttpExchange ex) throws IOException {
        send(ex, 200, "text/html", HtmlReportRenderer.render(current.get(), marketPrices, returnsTables, weightTables));
    }

    private void handleGetPortfolio(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            send(ex, 405, "application/json", JsonRenderer.error("Use GET"));
            return;
        }
        send(ex, 200, "application/json", JsonRenderer.render(current.get(), marketPrices));
    }

    private void handlePostTrades(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            send(ex, 405, "application/json", JsonRenderer.error("Use POST"));
            return;
        }
        byte[] body = ex.getRequestBody().readNBytes(MAX_UPLOAD_BYTES + 1);
        if (body.length > MAX_UPLOAD_BYTES) {
            send(ex, 413, "application/json", JsonRenderer.error("File too large (limit 1 MB)"));
            return;
        }
        try {
            List<Trade> trades = TradeCsvReader.read(new StringReader(new String(body, StandardCharsets.UTF_8)));
            Portfolio fresh = new Portfolio();
            fresh.applyTrades(trades);
            String json = JsonRenderer.render(fresh, marketPrices);   // fails here if a price is missing
            current.set(fresh);                                        // only swap in once everything succeeded
            send(ex, 200, "application/json", json);
        } catch (RuntimeException e) {                                 // bad CSV, selling more than held, missing price
            send(ex, 400, "application/json", JsonRenderer.error(e.getMessage()));
        }
    }

    private static void send(HttpExchange ex, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        } finally {
            ex.close();
        }
    }

    /** Starts serving requests. Call {@link #stop()} to shut it down. */
    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();   // otherwise the worker thread keeps the JVM alive
    }

    /** The port actually bound (useful when constructed with port 0 in tests). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getUrl() {
        return "http://localhost:" + getPort() + "/";
    }
}
