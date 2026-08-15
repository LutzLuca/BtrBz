package com.github.lutzluca.coflnet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoflnetBazaarClientTest {
    private static final String SNAPSHOT_JSON = """
        {
          "productId":"BOOSTER_COOKIE",
          "buyPrice":12033243.8,
          "buyVolume":9709,
          "buyMovingWeek":96159,
          "buyOrdersCount":624,
          "sellPrice":11800007.0,
          "sellVolume":26063,
          "sellMovingWeek":83707,
          "sellOrdersCount":1722,
          "timeStamp":"2026-08-13T01:42:41.908",
          "buyOrders":[{"amount":9,"pricePerUnit":12033243.8,"orders":1}],
          "sellOrders":[{"amount":55,"pricePerUnit":11800007.0,"orders":1}]
        }
        """;

    private final List<CoflnetBazaarClient> clients = new CopyOnWriteArrayList<>();
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUp() {
        clients.forEach(CoflnetBazaarClient::close);
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void parsesSnapshotAndTreatsOffsetlessTimestampAsUtc() throws Exception {
        HttpServer server = server(exchange -> json(exchange, 200, SNAPSHOT_JSON, "max-age=360"));
        CoflnetBazaarClient client = client(server);

        BazaarSnapshot snapshot = get(client.snapshot("BOOSTER_COOKIE")).orElseThrow();

        assertEquals("BOOSTER_COOKIE", snapshot.productId());
        assertEquals(12_033_243.8, snapshot.buyPrice());
        assertEquals(11_800_007.0, snapshot.sellPrice());
        assertEquals(Instant.parse("2026-08-13T01:42:41.908Z"), snapshot.timeStamp());
        assertEquals(new BazaarOrder(9, 12_033_243.8, 1), snapshot.buyOrders().getFirst());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.buyOrders().add(new BazaarOrder(1, 1, 1)));
    }

    @Test
    void selectsHourPathAndAllowsMissingBandsAndOffsetTimestamp() throws Exception {
        CopyOnWriteArrayList<String> targets = new CopyOnWriteArrayList<>();
        HttpServer server = server(exchange -> {
            targets.add(exchange.getRequestURI().toString());
            json(exchange, 200, """
                [{
                  "buy":10.5,
                  "sell":11.2,
                  "buyVolume":150000,
                  "sellVolume":200000,
                  "buyMovingWeek":2500000,
                  "sellMovingWeek":3000000,
                  "timestamp":"2026-08-13T03:00:00+02:00"
                }]
                """, "max-age=300");
        });
        CoflnetBazaarClient client = client(server);

        BazaarHistoryPoint point = get(client.history("GOLD_BLOCK", HistoryRange.Preset.HOUR)).getFirst();

        assertEquals(List.of("/api/bazaar/GOLD_BLOCK/history/hour"), targets);
        assertNull(point.minBuy());
        assertNull(point.maxBuy());
        assertNull(point.minSell());
        assertNull(point.maxSell());
        assertEquals(Instant.parse("2026-08-13T01:00:00Z"), point.timestamp());
    }

    @Test
    void selectsPresetAndCustomHistoryEndpoints() throws Exception {
        CopyOnWriteArrayList<URI> requests = new CopyOnWriteArrayList<>();
        HttpServer server = server(exchange -> {
            requests.add(exchange.getRequestURI());
            json(exchange, 200, "[]", "max-age=0");
        });
        CoflnetBazaarClient client = client(server);

        get(client.history("GOLD_BLOCK", HistoryRange.Preset.DAY));
        get(client.history("GOLD_BLOCK", HistoryRange.Preset.WEEK));
        get(client.history("GOLD_BLOCK", new HistoryRange.Custom(
            Instant.parse("2026-08-12T00:00:00Z"),
            Instant.parse("2026-08-13T00:00:00Z"))));

        assertEquals("/api/bazaar/GOLD_BLOCK/history/day", requests.get(0).toString());
        assertEquals("/api/bazaar/GOLD_BLOCK/history/week", requests.get(1).toString());
        assertEquals(
            "/api/bazaar/GOLD_BLOCK/history?start=2026-08-12T00%3A00%3A00Z&end=2026-08-13T00%3A00%3A00Z",
            requests.get(2).toString());
    }

    @Test
    void deduplicatesInFlightRequestsAndCachesByFullUri() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            requestArrived.countDown();
            try {
                assertTrue(releaseResponse.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            json(exchange, 200, SNAPSHOT_JSON, "public, max-age=60");
        });
        CoflnetBazaarClient client = client(server);

        CompletableFuture<Optional<BazaarSnapshot>> first = client.snapshot("BOOSTER_COOKIE").toCompletableFuture();
        assertTrue(requestArrived.await(3, TimeUnit.SECONDS));
        CompletableFuture<Optional<BazaarSnapshot>> second = client.snapshot("BOOSTER_COOKIE").toCompletableFuture();
        releaseResponse.countDown();

        assertTrue(first.get(3, TimeUnit.SECONDS).isPresent());
        assertTrue(second.get(3, TimeUnit.SECONDS).isPresent());
        assertTrue(get(client.snapshot("BOOSTER_COOKIE")).isPresent());
        assertEquals(1, calls.get());
    }

    @Test
    void subtractsCloudflareAgeFromLocalCacheLifetime() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Age", "60");
            json(exchange, 200, SNAPSHOT_JSON, "public, max-age=60");
        });
        CoflnetBazaarClient client = client(server);

        assertTrue(get(client.snapshot("BOOSTER_COOKIE")).isPresent());
        assertTrue(get(client.snapshot("BOOSTER_COOKIE")).isPresent());

        assertEquals(2, calls.get());
    }

    @Test
    void mapsNoContentSnapshotToEmptyOptional() throws Exception {
        HttpServer server = server(exchange -> empty(exchange, 204, "max-age=360"));
        CoflnetBazaarClient client = client(server);

        assertFalse(get(client.snapshot("UNKNOWN_ITEM")).isPresent());
    }

    @Test
    void respectsRetryAfterBeforeRetryingRateLimitedResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                json(exchange, 429, "{\"message\":\"slow down\"}", null);
            } else {
                json(exchange, 200, "[]", "max-age=60");
            }
        });
        CoflnetBazaarClient client = client(server);

        assertTrue(get(client.history("GOLD_BLOCK", HistoryRange.Preset.HOUR)).isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void reportsOrdinaryErrorsAsTypedExceptions() {
        HttpServer server = server(exchange -> json(exchange, 400, "{\"title\":\"bad request\"}", null));
        CoflnetBazaarClient client = client(server);

        CompletionException completion = assertThrows(CompletionException.class,
            () -> client.history("GOLD_BLOCK", HistoryRange.Preset.DAY).toCompletableFuture().join());
        CoflnetApiException exception = assertInstanceOf(CoflnetApiException.class, completion.getCause());
        assertEquals(400, exception.statusCode());
        assertEquals("{\"title\":\"bad request\"}", exception.responseBody());
    }

    @Test
    void rejectsJsonNullSnapshotAsMalformed() {
        HttpServer server = server(exchange -> json(exchange, 200, "null", "max-age=360"));
        CoflnetBazaarClient client = client(server);

        CompletionException completion = assertThrows(CompletionException.class,
            () -> client.snapshot("GOLD_BLOCK").toCompletableFuture().join());
        CoflnetApiException exception = assertInstanceOf(CoflnetApiException.class, completion.getCause());
        assertEquals(200, exception.statusCode());
        assertEquals("null", exception.responseBody());
    }

    @Test
    void rejectsNullHistoryAsMalformedWithoutCachingIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> json(
            exchange,
            200,
            calls.incrementAndGet() == 1 ? "null" : "[]",
            "max-age=3600"));
        CoflnetBazaarClient client = client(server);

        CompletionException completion = assertThrows(CompletionException.class,
            () -> client.history("GOLD_BLOCK", HistoryRange.Preset.WEEK).toCompletableFuture().join());
        assertInstanceOf(CoflnetApiException.class, completion.getCause());
        assertTrue(get(client.history("GOLD_BLOCK", HistoryRange.Preset.WEEK)).isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void requestsAfterCloseFailThroughTheCompletionStage() {
        CoflnetBazaarClient client = CoflnetBazaarClient.create();
        clients.add(client);
        client.close();

        CompletionException completion = assertThrows(CompletionException.class,
            () -> client.snapshot("GOLD_BLOCK").toCompletableFuture().join());
        assertInstanceOf(CoflnetApiException.class, completion.getCause());
    }

    @Test
    void validatesTagsAndCustomRangesBeforeIssuingRequests() {
        assertThrows(IllegalArgumentException.class,
            () -> CoflnetBazaarClient.create().snapshot("../snapshot"));
        assertThrows(IllegalArgumentException.class,
            () -> CoflnetBazaarClient.create().snapshot(".."));
        assertThrows(IllegalArgumentException.class,
            () -> new HistoryRange.Custom(Instant.EPOCH, Instant.EPOCH));
    }

    private CoflnetBazaarClient client(HttpServer server) {
        CoflnetBazaarClient client = CoflnetBazaarClient.builder()
            .baseUri(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"))
            .build();
        clients.add(client);
        return client;
    }

    private HttpServer server(Handler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            servers.add(server);
            return server;
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static <T> T get(java.util.concurrent.CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static void json(HttpExchange exchange, int status, String body, String cacheControl) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (cacheControl != null) {
            exchange.getResponseHeaders().add("Cache-Control", cacheControl);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void empty(HttpExchange exchange, int status, String cacheControl) throws IOException {
        if (cacheControl != null) {
            exchange.getResponseHeaders().add("Cache-Control", cacheControl);
        }
        exchange.sendResponseHeaders(status, -1);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
