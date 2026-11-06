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
import java.util.function.LongSupplier;

/**
 * Local web dashboard on the JDK's built-in HTTP server.
 *
 * Routes:
 *   GET  /               the JavaScript dashboard (src/main/resources/web/index.html)
 *   GET  /report         the server-rendered HTML report, including any --optimize / --bayesian results
 *   GET  /api/portfolio  current portfolio as JSON
 *   POST /api/trades     body = trade CSV text; replaces the trade history, returns new JSON
 *   GET  /healthz        "ok" (for hosting platforms' health checks)
 *
 * <p><b>Public-demo mode.</b> By default the server holds ONE portfolio and every upload replaces
 * it, which is right for a dashboard on your own machine but wrong on the internet: one visitor's
 * upload would change what every other visitor sees. In public-demo mode an upload is validated
 * and the resulting JSON is returned to the uploader only; the shared portfolio never changes.
 * Uploads are also rate-limited, and requests are served by a small thread pool so one slow
 * client cannot block the rest.</p>
 */
public final class PortfolioWebServer {

    /** Uploads larger than this are rejected (stops a huge body from exhausting memory). */
    static final int MAX_UPLOAD_BYTES = 1_000_000;

    /** Public-demo mode: at most this many upload attempts per minute, across all visitors. */
    static final int MAX_UPLOADS_PER_MINUTE = 60;

    private static final String CSP_PAGE = "default-src 'none'; style-src 'unsafe-inline'; "
            + "script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private static final String CSP_STATIC = "default-src 'none'; style-src 'unsafe-inline'; "
            + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";   // no scripts at all

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicReference<Portfolio> current;
    private final Map<String, BigDecimal> marketPrices;
    private final List<HtmlReportRenderer.ReturnsTable> returnsTables;
    private final List<HtmlReportRenderer.WeightTable> weightTables;
    private final boolean publicDemo;
    private final LongSupplier clockMillis;
    private long windowStartMillis;
    private int uploadsInWindow;

    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices, int port) throws IOException {
        this(portfolio, marketPrices, List.of(), List.of(), port);
    }

    /**
     * Local mode: listens on localhost only, one shared portfolio.
     *
     * @param returnsTables Black-Litterman prior-vs-posterior expected-return tables, shown on /report (from --bayesian); empty if none.
     * @param weightTables   suggested-weights tables, shown on /report (from --optimize and/or --bayesian); empty if none.
     */
    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices,
                              List<HtmlReportRenderer.ReturnsTable> returnsTables,
                              List<HtmlReportRenderer.WeightTable> weightTables,
                              int port) throws IOException {
        this(portfolio, marketPrices, returnsTables, weightTables, "localhost", port, false, System::currentTimeMillis);
    }

    /**
     * @param host          interface to bind: "localhost" on your machine, "0.0.0.0" on a hosting platform.
     * @param publicDemo    true = uploads never change the shared portfolio (see class comment).
     * @param clockMillis   time source, replaceable in tests (used by the upload rate limit).
     */
    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices,
                              List<HtmlReportRenderer.ReturnsTable> returnsTables,
                              List<HtmlReportRenderer.WeightTable> weightTables,
                              String host, int port, boolean publicDemo, LongSupplier clockMillis) throws IOException {
        this.current = new AtomicReference<>(portfolio);
        this.marketPrices = marketPrices;
        this.returnsTables = returnsTables;
        this.weightTables = weightTables;
        this.publicDemo = publicDemo;
        this.clockMillis = clockMillis;
        this.windowStartMillis = clockMillis.getAsLong();
        this.executor = publicDemo ? Executors.newFixedThreadPool(4) : Executors.newSingleThreadExecutor();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handlePage);
        server.createContext("/report", this::handleReport);
        server.createContext("/healthz", ex -> send(ex, 200, "text/plain", "ok"));
        server.createContext("/api/portfolio", this::handleGetPortfolio);
        server.createContext("/api/trades", this::handlePostTrades);
        server.setExecutor(executor);
    }

    /** Fixed one-minute window shared by all visitors; true if this upload may proceed. */
    private synchronized boolean allowUpload() {
        long now = clockMillis.getAsLong();
        if (now - windowStartMillis >= 60_000) {
            windowStartMillis = now;
            uploadsInWindow = 0;
        }
        return ++uploadsInWindow <= MAX_UPLOADS_PER_MINUTE;
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
        if (publicDemo && !allowUpload()) {
            ex.getResponseHeaders().set("Retry-After", "60");
            send(ex, 429, "application/json", JsonRenderer.error("Too many uploads, try again in a minute"));
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
            if (!publicDemo) {
                current.set(fresh);                                    // only swap in once everything succeeded
            }                                                          // (public demo: answer the uploader only)
            send(ex, 200, "application/json", json);
        } catch (RuntimeException e) {                                 // bad CSV, selling more than held, missing price
            send(ex, 400, "application/json", JsonRenderer.error(e.getMessage()));
        }
    }

    private static void send(HttpExchange ex, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        if (type.equals("text/html")) {
            // The dashboard page needs its own inline script; the server-rendered report has none.
            boolean dashboard = ex.getRequestURI().getPath().equals("/");
            ex.getResponseHeaders().set("Content-Security-Policy", dashboard ? CSP_PAGE : CSP_STATIC);
        }
        if (type.equals("application/json")) {
            ex.getResponseHeaders().set("Cache-Control", "no-store");
        }
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
