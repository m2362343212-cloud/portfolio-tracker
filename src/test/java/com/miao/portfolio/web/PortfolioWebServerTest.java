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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioWebServerTest {

    private PortfolioWebServer server;
    private final HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    @BeforeEach
    void start() throws Exception {
        server = new PortfolioWebServer(new Portfolio(), Map.of("AAPL", new BigDecimal("200.00")), 0);  // port 0 = any free port
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(server.getUrl() + path.substring(1)))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void validCsvReturnsPortfolioJson() throws Exception {
        HttpResponse<String> res = post("/api/trades",
                "date,symbol,side,quantity,price\n2026-01-05,AAPL,BUY,10,150.00\n");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"unrealizedPnl\":\"500"), res.body());   // (200 - 150) x 10
    }

    @Test
    void malformedCsvReturns400AndKeepsOldPortfolio() throws Exception {
        HttpResponse<String> res = post("/api/trades", "date,symbol,side,quantity,price\nnot,a,valid,row\n");
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("\"error\""));
        HttpResponse<String> after = client.send(HttpRequest.newBuilder(URI.create(server.getUrl() + "api/portfolio")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(after.body().contains("\"open\":[]"), after.body());
    }

    @Test
    void sellingMoreThanHeldReturns400() throws Exception {
        HttpResponse<String> res = post("/api/trades", "date,symbol,side,quantity,price\n2026-01-05,AAPL,SELL,5,150.00\n");
        assertEquals(400, res.statusCode());
    }

    @Test
    void getOnTradesEndpointReturns405() throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(server.getUrl() + "api/trades")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, res.statusCode());
    }
}
