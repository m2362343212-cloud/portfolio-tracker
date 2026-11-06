package com.miao.portfolio.web;

import com.miao.portfolio.model.Portfolio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public-demo mode: uploads must not leak between visitors, and abuse is bounded. */
class PortfolioWebServerPublicDemoTest {

    private static final String CSV = "date,symbol,side,quantity,price\n2026-01-05,AAPL,BUY,10,150.00\n";

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    private PortfolioWebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new PortfolioWebServer(new Portfolio(), Map.of("AAPL", new BigDecimal("200.00")),
                List.of(), List.of(), "localhost", 0, true, clock::get);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.getUrl() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.getUrl() + "api/trades"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void uploaderSeesTheirResultButOtherVisitorsDoNot() throws Exception {
        HttpResponse<String> res = post(CSV);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"unrealizedPnl\":\"500"), res.body());       // (200 - 150) x 10
        assertTrue(get("api/portfolio").body().contains("\"open\":[]"));               // shared portfolio untouched
        assertFalse(get("report").body().contains("AAPL"));
    }

    @Test
    void uploadsAreRateLimitedThenResetAfterAMinute() throws Exception {
        for (int i = 0; i < PortfolioWebServer.MAX_UPLOADS_PER_MINUTE; i++) {
            assertEquals(200, post(CSV).statusCode());
        }
        HttpResponse<String> limited = post(CSV);
        assertEquals(429, limited.statusCode());
        assertEquals("60", limited.headers().firstValue("Retry-After").orElse(""));
        clock.addAndGet(60_000);
        assertEquals(200, post(CSV).statusCode());
    }

    @Test
    void healthCheckAndSecurityHeaders() throws Exception {
        assertEquals("ok", get("healthz").body());
        HttpResponse<String> page = get("");
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
        assertEquals("DENY", page.headers().firstValue("X-Frame-Options").orElse(""));
        assertFalse(get("report").headers().firstValue("Content-Security-Policy").orElse("").contains("script-src"));
        assertEquals("no-store", get("api/portfolio").headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void oversizedUploadIsStillRejected() throws Exception {
        assertEquals(413, post("x".repeat(PortfolioWebServer.MAX_UPLOAD_BYTES + 1)).statusCode());
    }
}
