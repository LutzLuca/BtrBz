package com.github.lutzluca.coflnet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CoflnetClientTest {
    private static final HistoryQuery RANGE = new HistoryQuery(Instant.parse("2025-10-01T00:00:00Z"),
        Instant.parse("2025-10-02T00:00:00Z"));

    @Test
    void sharedInterestRefreshAndFailedRefreshPreserveCachedData() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                await(release);
            }
            respond(exchange, call > 1 ? 400 : 200,
                "[{\"timestamp\":\"2025-10-01T02:00:00\",\"buy\":4},"
                    + "{\"timestamp\":\"2025-10-01T00:00:00Z\",\"sellVolume\":12,\"sell\":0,"
                    + "\"minBuy\":-1,\"maxBuy\":\"NaN\",\"buyVolume\":-5,\"buyMovingWeek\":0},"
                    + "{\"timestamp\":\"2025-10-01T02:00:00Z\",\"buy\":99}]");
        })) {
            CoflnetRequest<HistoryResponse> first = fixture.client.history("DIAMOND", RANGE, false);
            Assertions.assertTrue(entered.await(3, TimeUnit.SECONDS));
            CoflnetRequest<HistoryResponse> second = fixture.client.history("DIAMOND", RANGE, true);
            first.close();
            release.countDown();
            HistoryResponse response = second.completion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(2, response.points().size());
            Assertions.assertNull(response.points().getFirst().buy());
            Assertions.assertEquals(12L, response.points().getFirst().sellVolume());
            Assertions.assertNull(response.points().getFirst().sell());
            Assertions.assertNull(response.points().getFirst().minBuy());
            Assertions.assertNull(response.points().getFirst().maxBuy());
            Assertions.assertNull(response.points().getFirst().buyVolume());
            Assertions.assertEquals(0L, response.points().getFirst().buyMovingWeek());
            Assertions.assertEquals(4.0, response.points().getLast().buy());
            Assertions.assertEquals(RANGE.start(), response.coverageStart());
            Assertions.assertThrows(Exception.class, () -> fixture.client.history("DIAMOND", RANGE, true).completion()
                .toCompletableFuture().get(3, TimeUnit.SECONDS));
            Assertions.assertSame(response,
                fixture.client.history("DIAMOND", RANGE, false).completion().toCompletableFuture().get());
            Assertions.assertEquals(2, calls.get());
        }
    }

    @Test
    void lastSubscriberDisposalGuardsLateResponseAndDoesNotBlockOtherWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (Fixture fixture = new Fixture(exchange -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
            }
            respond(exchange, 200, "[]");
        })) {
            CoflnetRequest<HistoryResponse> abandoned = fixture.client.history("DIAMOND", RANGE, false);
            Assertions.assertTrue(entered.await(3, TimeUnit.SECONDS));
            abandoned.close();
            CoflnetRequest<HistoryResponse> replacement = fixture.client.history("DIAMOND", RANGE, false);
            release.countDown();
            Assertions
                .assertTrue(replacement.completion().toCompletableFuture().get(4, TimeUnit.SECONDS).points().isEmpty());
            Assertions.assertEquals(2, calls.get());
            Assertions.assertTrue(abandoned.completion().toCompletableFuture().isCompletedExceptionally());
        }
    }

    @Test
    void retryAfterCooldownAndAgePreventPrematureReuse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long[] requests = new long[4];
        try (Fixture fixture = new Fixture(exchange -> {
            int call = calls.getAndIncrement();
            requests[Math.min(call, 3)] = System.nanoTime();
            if (call == 0) {
                exchange.getResponseHeaders().set("Retry-After", "1");
                respond(exchange, 429, "");
            } else if (call == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 502, "");
            } else {
                exchange.getResponseHeaders().set("Age", "600");
                respond(exchange, 204, "");
            }
        })) {
            HistoryResponse result = fixture.client.history("DIAMOND", RANGE, false).completion().toCompletableFuture()
                .get(6, TimeUnit.SECONDS);
            Assertions.assertTrue(result.points().isEmpty());
            Assertions.assertNull(result.coverageStart());
            Assertions.assertTrue(requests[1] - requests[0] >= TimeUnit.MILLISECONDS.toNanos(950));
            fixture.client.history("DIAMOND", RANGE, false).completion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(4, calls.get());
        }
    }

    @Test
    void historicalMayorPerksRemainPartialAndOversizedBodiesFailTyped() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("mayor")) {
                Assertions.assertTrue(exchange.getRequestURI().getQuery().contains("from="));
                respond(exchange, 200,
                    "[{}, {\"start\":\"10/01/2025 00:00:00 +00:00\",\"end\":\"10/02/2025 00:00:00\","
                        + "\"winner\":{\"name\":\"Diana\",\"perks\":[{\"name\":\"Pet XP Buff\","
                        + "\"description\":null}]}}]");
            } else {
                exchange.sendResponseHeaders(200, 0);
                try {
                    byte[] chunk = new byte[64 * 1024];
                    for (int i = 0; i < 80; i++) {
                        exchange.getResponseBody().write(chunk);
                    }
                } catch (IOException _) {
                    // The client closes the stream as soon as its body limit is reached.
                } finally {
                    exchange.close();
                }
            }
        })) {
            MayorResponse response = fixture.client.mayors(RANGE.start(), RANGE.end(), false).completion()
                .toCompletableFuture().get(3, TimeUnit.SECONDS);
            Assertions.assertEquals(1, response.terms().size());
            Assertions.assertEquals(RANGE.start(), response.terms().getFirst().start());
            Assertions.assertEquals("Pet XP Buff", response.terms().getFirst().perks().getFirst().name());
            Assertions.assertNull(response.terms().getFirst().perks().getFirst().description());
            Exception failure = Assertions.assertThrows(Exception.class, () -> fixture.client
                .history("DIAMOND", RANGE, false).completion().toCompletableFuture().get(3, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(CoflnetException.class, failure.getCause());
            Assertions.assertEquals(CoflnetException.Kind.BODY_LIMIT, ((CoflnetException) failure.getCause()).kind());
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=600");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        private final CoflnetClient client;

        private Fixture(com.sun.net.httpserver.HttpHandler handler) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/api/", handler);
            this.server.setExecutor(this.executor);
            this.server.start();
            this.client = new CoflnetClient(
                URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/api/"), Clock.systemUTC());
        }

        @Override
        public void close() {
            this.client.close();
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }
}
