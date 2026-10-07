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
import java.util.concurrent.CopyOnWriteArrayList;
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
    void orderBookMakesNoRequestsAndHistoryOnlyLoadsItsSelectedRangeAndEnabledMayors() throws Exception {
        try (var fixture = new Fixture(); var session = fixture.session(new BazaarData(), () -> true)) {
            session.select(FIRST);
            session.range(ItemInfoRange.Day);
            session.mayorVisible(true);
            session.select(SECOND);
            session.select(FIRST);
            fixture.clock.advanceMinute();
            fixture.clock.advanceMinute();
            session.refresh();
            session.mayorVisible(false);
            Assertions.assertNull(fixture.dispatch.poll(250, TimeUnit.MILLISECONDS));
            Assertions.assertEquals(0, fixture.calls.get());

            session.historyVisible(true);
            fixture.completions(1).forEach(Runnable::run);
            Assertions.assertEquals(fixture.clock.instant(), session.data().query().end());
            Assertions.assertEquals(List.of("/api/bazaar/FIRST/history/day"), fixture.paths);
            var retained = session.data().history().value();
            Assertions.assertNotNull(retained);
            session.historyVisible(true);
            session.select(FIRST);

            session.historyVisible(false);
            fixture.clock.advanceMinute();
            session.refresh();
            session.mayorVisible(true);
            Assertions.assertNull(fixture.dispatch.poll(250, TimeUnit.MILLISECONDS));
            Assertions.assertEquals(1, fixture.calls.get());
            session.historyVisible(true);
            fixture.completions(1).forEach(Runnable::run);
            Assertions.assertSame(retained, session.data().history().value());
            Assertions.assertEquals(List.of("/api/bazaar/FIRST/history/day", "/api/mayor"), fixture.paths);

            session.refresh();
            var pending = fixture.completions(2);
            session.historyVisible(false);
            pending.forEach(Runnable::run);
            Assertions.assertSame(retained, session.data().history().value());
            Assertions.assertFalse(session.data().history().updating());
            Assertions.assertFalse(session.data().mayors().updating());
            int callsAfterHistory = fixture.calls.get();
            session.select(SECOND);
            session.range(ItemInfoRange.Hour);
            session.mayorVisible(false);
            session.refresh();
            Assertions.assertNull(fixture.dispatch.poll(250, TimeUnit.MILLISECONDS));
            Assertions.assertEquals(callsAfterHistory, fixture.calls.get());
            Assertions.assertNull(session.data().history().value());
            session.historyVisible(true);
            fixture.completions(1).forEach(Runnable::run);
            Assertions.assertEquals("/api/bazaar/SECOND/history/hour", fixture.paths.getLast());
            Assertions.assertNull(fixture.dispatch.poll(250, TimeUnit.MILLISECONDS));
            Assertions.assertEquals(callsAfterHistory + 1, fixture.calls.get());
        }
    }

    @Test
    void queuedCompletionsCannotPublishAfterProductChangeRuntimeInvalidationOrClose() throws Exception {
        try (var fixture = new Fixture()) {
            var current = new AtomicBoolean(true);
            try (var session = fixture.session(new BazaarData(), current::get)) {
                session.historyVisible(true);
                session.select(FIRST);
                var obsolete = fixture.completions(1);
                session.select(SECOND);
                obsolete.forEach(Runnable::run);
                Assertions.assertEquals(SECOND, session.data().product());
                Assertions.assertNull(session.data().history().value());

                var pending = fixture.completions(1);
                current.set(false);
                pending.getFirst().run();
                Assertions.assertNull(session.data().history().value());
                current.set(true);
                session.select(FIRST);
                var closing = fixture.completions(1);
                session.close();
                closing.forEach(Runnable::run);
                Assertions.assertNull(session.data().history().value());
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
                session.historyVisible(true);
                session.select(FIRST);
                fixture.completions(1).forEach(Runnable::run);
                var retained = session.data().history().value();
                var retainedQuery = session.data().history().query();
                Assertions.assertNotNull(retained);
                Assertions.assertEquals(1, fixture.calls.get());
                Assertions.assertTrue(session.data().live().isPresent());
                runtime.hibernate();
                Assertions.assertTrue(session.data().live().isEmpty());
                Assertions.assertSame(retained, session.data().history().value());

                fixture.clock.advanceMinute();
                session.refresh();
                var superseded = fixture.completions(1);
                fixture.clock.advanceMinute();
                session.refresh();
                superseded.forEach(Runnable::run);
                Assertions.assertSame(retained, session.data().history().value());
                Assertions.assertEquals(retainedQuery, session.data().history().query());
                Assertions.assertTrue(session.data().history().updating());
                fixture.completions(1).forEach(Runnable::run);
                Assertions.assertEquals(session.data().query(), session.data().history().query());
                Assertions.assertEquals(3, fixture.calls.get());
                var customQuery = new HistoryQuery(session.data().query().start(), session.data().query().end());
                session.customRange(customQuery.start(), customQuery.end());
                Assertions.assertEquals(ItemInfoRange.Custom, session.data().range());
                fixture.completions(1).forEach(Runnable::run);
                Assertions.assertEquals(customQuery, session.data().history().query());
                fixture.clock.advanceMinute();
                session.refresh();
                Assertions.assertEquals(customQuery, session.data().query());
                fixture.completions(1).forEach(Runnable::run);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MutableClock clock = new MutableClock();
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final LinkedBlockingQueue<Runnable> dispatch = new LinkedBlockingQueue<>();
        private final HttpServer server;
        private final CoflnetClient client;

        private Fixture() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/api/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                this.paths.add(path);
                this.calls.incrementAndGet();
                byte[] body = (path.equals("/api/mayor")
                    ? "[]"
                    : "[{\"timestamp\":\"2026-10-05T10:00:00Z\",\"buy\":0.1}]")
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
