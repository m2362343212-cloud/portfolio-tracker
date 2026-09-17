package com.miao.portfolio.web;

import com.miao.portfolio.model.Portfolio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A minimal local web dashboard for a {@link Portfolio}, built entirely on
 * {@code com.sun.net.httpserver} — the lightweight HTTP server that ships
 * inside the JDK itself. No framework, no external dependency: just enough
 * of an HTTP server to serve one page.
 *
 * <p>This is deliberately not a "real" production web app (no routing
 * framework, no templating engine, no persistence) — it's a thin
 * presentation layer over the same {@link Portfolio}/{@link Position}
 * domain model the CLI report uses, which is the point: the accounting
 * logic doesn't know or care whether it's being viewed from a terminal
 * or a browser.</p>
 */
public final class PortfolioWebServer {

    private final HttpServer server;
    private final int port;

    public PortfolioWebServer(Portfolio portfolio, Map<String, BigDecimal> marketPrices, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        this.server.createContext("/", exchange -> handle(exchange, portfolio, marketPrices));
        this.server.setExecutor(Executors.newSingleThreadExecutor());
    }

    private static void handle(HttpExchange exchange, Portfolio portfolio, Map<String, BigDecimal> marketPrices) throws IOException {
        try {
            String html = HtmlReportRenderer.render(portfolio, marketPrices);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    /** Starts serving requests. Call {@link #stop()} to shut it down. */
    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return port;
    }

    public String getUrl() {
        return "http://localhost:" + port + "/";
    }
}
