package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.runtime.Activation;
import com.github.lutzluca.btrbz.core.runtime.FeatureRuntime;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.coflnet.CoflnetClient;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ItemInfoSessionTest {
    private static final ProductIdentity FIRST = ProductIdentity.fromRuntime("First", "FIRST", null);
    private static final ProductIdentity SECOND = ProductIdentity.fromRuntime("Second", "SECOND", null);

    @Test
    void queuedCompletionsCannotPublishAfterProductChangeRuntimeInvalidationOrClose() throws Exception {
        try (var fixture = new Fixture()) {
            var current = new AtomicBoolean(true);
            try (var session = fixture.session(new BazaarData(), current::get)) {
                session.select(FIRST);
                var obsolete = fixture.completions(2);
                session.select(SECOND);
                obsolete.forEach(Runnable::run);
                Assertions.assertEquals(SECOND, session.data().product());
                Assertions.assertNull(session.data().history().value());
                Assertions.assertNull(session.data().reference().value());

                var pending = fixture.completions(2);
                current.set(false);
                pending.getFirst().run();
                Assertions.assertNull(session.data().history().value());
                Assertions.assertNull(session.data().reference().value());
                session.close();
                pending.getLast().run();
                Assertions.assertNull(session.data().history().value());
                Assertions.assertNull(session.data().reference().value());
            }
        }
    }

    @Test
    void hibernationRetainsHistoryAndRefreshKeepsHonestProvenanceUntilNewestCoverageArrives() throws Exception {
        try (var fixture = new Fixture()) {
            var market = new BazaarData();
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), market,
                () -> {}, () -> {}, () -> {}, () -> {});
            runtime.activate();
            var products = new Gson().fromJson("""
                {"products":{"FIRST":{"buy_summary":[{"pricePerUnit":0.1,"amount":1,"orders":1}]}}}
                """, SkyBlockBazaarReply.class).getProducts();
            market.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(products));
            try (var session = fixture.session(market, runtime::isRunning)) {
                session.select(FIRST);
                fixture.completions(2).forEach(Runnable::run);
                var retained = session.data().history().value();
                var retainedQuery = session.data().history().query();
                Assertions.assertNotNull(retained);
                Assertions.assertSame(retained, session.data().reference().value());
                Assertions.assertEquals(1, fixture.calls.get());
                Assertions.assertTrue(session.data().live().isPresent());
                runtime.hibernate();
                Assertions.assertTrue(session.data().live().isEmpty());
                Assertions.assertSame(retained, session.data().history().value());

                fixture.clock.advanceMinute();
                session.refresh();
                var superseded = fixture.completions(2);
                fixture.clock.advanceMinute();
                session.refresh();
                superseded.forEach(Runnable::run);
                Assertions.assertSame(retained, session.data().history().value());
                Assertions.assertEquals(retainedQuery, session.data().history().query());
                Assertions.assertTrue(session.data().history().updating());
                fixture.completions(2).forEach(Runnable::run);
                Assertions.assertEquals(session.data().query(), session.data().history().query());

                session.historyVisible(false);
                fixture.clock.advanceMinute();
                session.refresh();
                fixture.completions(1).forEach(Runnable::run);
                Assertions.assertNotEquals(session.data().query(), session.data().history().query());
                session.historyVisible(true);
                fixture.completions(1).forEach(Runnable::run);
                Assertions.assertEquals(session.data().query(), session.data().history().query());
                Assertions.assertEquals(4, fixture.calls.get());
                var customQuery = new HistoryQuery(session.data().query().start(), session.data().query().end());
                session.customRange(customQuery.start(), customQuery.end());
                Assertions.assertEquals(ItemInfoRange.Custom, session.data().range());
                fixture.completions(1).forEach(Runnable::run);
                Assertions.assertEquals(customQuery, session.data().history().query());
                fixture.clock.advanceMinute();
                session.refresh();
                Assertions.assertEquals(customQuery, session.data().query());
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MutableClock clock = new MutableClock();
        private final AtomicInteger calls = new AtomicInteger();
        private final LinkedBlockingQueue<Runnable> dispatch = new LinkedBlockingQueue<>();
        private final HttpServer server;
        private final CoflnetClient client;

        private Fixture() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/api/", exchange -> {
                this.calls.incrementAndGet();
                byte[] body = "[{\"timestamp\":\"2026-10-05T10:00:00Z\",\"buy\":0.1}]"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=600");
                exchange.sendResponseHeaders(200, body.length);
                try (var response = exchange.getResponseBody()) {
                    response.write(body);
                }
                exchange.close();
            });
            this.server.start();
            this.client = new CoflnetClient(
                URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/api/"), this.clock);
        }

        private ItemInfoSession session(BazaarData market, BooleanSupplier current) {
            return new ItemInfoSession(market, this.client, current, this.dispatch::add, this.clock,
                ItemInfoRange.Week, false);
        }

        private List<Runnable> completions(int count) throws InterruptedException {
            var completions = new ArrayList<Runnable>();
            for (int i = 0; i < count; i++) {
                var completion = this.dispatch.poll(5, TimeUnit.SECONDS);
                Assertions.assertNotNull(completion, "Expected a queued client-thread completion");
                completions.add(completion);
            }
            return completions;
        }

        @Override
        public void close() {
            this.client.close();
            this.server.stop(0);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-05T10:00:00Z");

        private void advanceMinute() {
            this.now = this.now.plusSeconds(60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(this.now, zone);
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}
