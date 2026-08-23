package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Empty;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Loading;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Success;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMarketUpdate;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import com.github.lutzluca.coflnet.BazaarSnapshot;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import com.github.lutzluca.coflnet.HistoryRange;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import org.junit.jupiter.api.Test;

class BazaarItemInfoDataProviderTest {
    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("Item", "ITEM", null);
    private static final Instant START = Instant.parse("2026-08-20T10:00:00Z");

    @Test
    void directOrderBookEntryMakesNoCoflnetRequest() {
        var client = new FakeClient();
        var provider = provider(client, InitialMode.OrderBook, new MutableClock(START));

        assertTrue(client.historyRequests.isEmpty());
        assertTrue(client.refreshRequests.isEmpty());
        assertFalse(provider.state().historyOpened());

        provider.close();
    }

    @Test
    void firstHistoryEntryLoadsSelectedRangeAndWeek() {
        var client = new FakeClient();
        var provider = provider(client, InitialMode.OrderBook, new MutableClock(START));

        provider.setMode(InitialMode.History);

        assertEquals(List.of(HistoryRange.Preset.DAY, HistoryRange.Preset.WEEK), client.requestedRanges());
        assertTrue(provider.state().historyOpened());
        assertInstanceOf(Loading.class, provider.state().history().get(BazaarItemInfoRange.Day).data());
        assertInstanceOf(Loading.class, provider.state().history().get(BazaarItemInfoRange.Week).data());

        provider.close();
    }

    @Test
    void selectingWeekInitiallyUsesOneSharedRequest() {
        var client = new FakeClient();
        var config = new BazaarItemInfoConfig();
        config.selectedRange = BazaarItemInfoRange.Week;
        var provider = provider(client, InitialMode.History, new MutableClock(START), config, Runnable::run, _ -> {});

        assertEquals(List.of(HistoryRange.Preset.WEEK), client.requestedRanges());

        provider.close();
    }

    @Test
    void rangeCompletionsRemainIndependentAfterSelectionChanges() {
        var client = new FakeClient();
        var provider = provider(client, InitialMode.History, new MutableClock(START));

        provider.selectRange(BazaarItemInfoRange.Hour);
        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(3), point(1), point(2)));

        var state = provider.state();
        assertEquals(BazaarItemInfoRange.Hour, state.selectedRange());
        var day = assertInstanceOf(Success.class, state.history().get(BazaarItemInfoRange.Day).data());
        var history = (BazaarItemInfoViewData.History) day.value();
        assertEquals(List.of(1L, 2L, 3L),
            history.points().stream().map(value -> value.timestamp().getEpochSecond()).toList());
        assertInstanceOf(Loading.class, state.history().get(BazaarItemInfoRange.Hour).data());

        provider.close();
    }

    @Test
    void failedRefreshRetainsDataAndCheckedTime() {
        var client = new FakeClient();
        var clock = new MutableClock(START);
        var provider = provider(client, InitialMode.History, clock);
        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(1)));
        client.normal(BazaarItemInfoRange.Week).complete(List.of(point(1)));
        Instant checked = provider.state().selectedHistory().checkedAt().orElseThrow();

        clock.advance(Duration.ofMinutes(10));
        provider.refresh();
        client.refresh(BazaarItemInfoRange.Day).completeExceptionally(new IllegalStateException("offline"));

        var selected = provider.state().selectedHistory();
        var failure = assertInstanceOf(Failure.class, selected.data());
        assertEquals("offline", failure.message());
        assertTrue(failure.previous().isPresent());
        assertEquals(checked, selected.checkedAt().orElseThrow());

        provider.close();
    }

    @Test
    void successfulEmptyResponseRecordsCheckedTime() {
        var client = new FakeClient();
        var provider = provider(client, InitialMode.History, new MutableClock(START));

        client.normal(BazaarItemInfoRange.Day).complete(List.of());

        assertInstanceOf(Empty.class, provider.state().selectedHistory().data());
        assertEquals(START, provider.state().selectedHistory().checkedAt().orElseThrow());

        provider.close();
    }

    @Test
    void manualRefreshIncludesWeekOnlyWhenStale() {
        var client = new FakeClient();
        var clock = new MutableClock(START);
        var provider = provider(client, InitialMode.History, clock);
        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(1)));
        client.normal(BazaarItemInfoRange.Week).complete(List.of(point(1)));

        clock.advance(Duration.ofMinutes(59));
        provider.refresh();
        assertEquals(List.of(HistoryRange.Preset.DAY), client.refreshedRanges());
        client.refresh(BazaarItemInfoRange.Day).complete(List.of(point(2)));

        clock.advance(Duration.ofMinutes(2));
        provider.refresh();
        assertEquals(
            List.of(HistoryRange.Preset.DAY, HistoryRange.Preset.DAY, HistoryRange.Preset.WEEK),
            client.refreshedRanges());

        provider.close();
    }

    @Test
    void liveUpdateChangesOnlyLiveSection() {
        var data = new BazaarData();
        var client = new FakeClient();
        var config = new BazaarItemInfoConfig();
        var provider = new BazaarItemInfoDataProvider(
            data, client, PRODUCT, "ITEM", InitialMode.History, new MutableClock(START),
            Runnable::run, config, _ -> {});
        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(1)));
        var historyBefore = provider.state().history();

        data.onUpdate(new BazaarMarketUpdate(START.toEpochMilli(), Map.of("ITEM", product(9, 10))));

        assertEquals(historyBefore, provider.state().history());
        assertEquals(10, provider.state().live().buyPrice().orElseThrow());
        assertEquals(9, provider.state().live().sellPrice().orElseThrow());

        provider.close();
    }

    @Test
    void disposalIgnoresRequestsAndMarketPublications() {
        var data = new BazaarData();
        var client = new FakeClient();
        var provider = new BazaarItemInfoDataProvider(
            data, client, PRODUCT, "ITEM", InitialMode.History, new MutableClock(START),
            Runnable::run, new BazaarItemInfoConfig(), _ -> {});
        var before = provider.state();

        provider.close();
        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(1)));
        data.onUpdate(new BazaarMarketUpdate(START.toEpochMilli(), Map.of("ITEM", product(9, 10))));

        assertEquals(before, provider.state());
    }

    @Test
    void notificationsUseTheChosenUiExecutor() {
        var client = new FakeClient();
        var executor = new QueuedExecutor();
        var delivered = new ArrayList<BazaarItemInfoViewData.ScreenState>();
        var provider = provider(
            client, InitialMode.History, new MutableClock(START),
            new BazaarItemInfoConfig(), executor, delivered::add);

        assertTrue(delivered.isEmpty());
        executor.runAll();
        assertFalse(delivered.isEmpty());

        client.normal(BazaarItemInfoRange.Day).complete(List.of(point(1)));
        assertFalse(provider.state().selectedHistory().data() instanceof Success<?>);
        executor.runAll();
        assertInstanceOf(Success.class, provider.state().selectedHistory().data());

        provider.close();
    }

    private static BazaarItemInfoDataProvider provider(
        FakeClient client,
        InitialMode mode,
        MutableClock clock
    ) {
        return provider(client, mode, clock, new BazaarItemInfoConfig(), Runnable::run, _ -> {});
    }

    private static BazaarItemInfoDataProvider provider(
        FakeClient client,
        InitialMode mode,
        MutableClock clock,
        BazaarItemInfoConfig config,
        Executor executor,
        java.util.function.Consumer<BazaarItemInfoViewData.ScreenState> listener
    ) {
        return new BazaarItemInfoDataProvider(
            new BazaarData(), client, PRODUCT, "ITEM", mode, clock, executor, config, listener);
    }

    private static BazaarHistoryPoint point(long epochSecond) {
        return new BazaarHistoryPoint(
            epochSecond, epochSecond + 1, null, null, null, null,
            10, 20, 0, 0, Instant.ofEpochSecond(epochSecond));
    }

    private static Product product(double buyOrder, double sellOffer) {
        var reply = new SkyBlockBazaarReply();
        var product = reply.new Product();
        var buy = product.new Summary();
        var sell = product.new Summary();
        setField(buy, "pricePerUnit", buyOrder);
        setField(buy, "amount", 2L);
        setField(buy, "orders", 1L);
        setField(sell, "pricePerUnit", sellOffer);
        setField(sell, "amount", 3L);
        setField(sell, "orders", 1L);
        setField(product, "sellSummary", List.of(buy));
        setField(product, "buySummary", List.of(sell));
        return product;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private record Request(HistoryRange range, CompletableFuture<List<BazaarHistoryPoint>> future) {}

    private static final class FakeClient implements CoflnetBazaarClient {
        private final List<Request> historyRequests = new ArrayList<>();
        private final List<Request> refreshRequests = new ArrayList<>();

        @Override
        public CompletionStage<Optional<BazaarSnapshot>> snapshot(String itemTag) {
            throw new AssertionError("Item Info must not request Coflnet snapshots");
        }

        @Override
        public CompletionStage<List<BazaarHistoryPoint>> history(String itemTag, HistoryRange range) {
            var future = new CompletableFuture<List<BazaarHistoryPoint>>();
            this.historyRequests.add(new Request(range, future));
            return future;
        }

        @Override
        public CompletionStage<List<BazaarHistoryPoint>> refreshHistory(String itemTag, HistoryRange range) {
            var future = new CompletableFuture<List<BazaarHistoryPoint>>();
            this.refreshRequests.add(new Request(range, future));
            return future;
        }

        List<HistoryRange> requestedRanges() {
            return this.historyRequests.stream().map(Request::range).toList();
        }

        List<HistoryRange> refreshedRanges() {
            return this.refreshRequests.stream().map(Request::range).toList();
        }

        CompletableFuture<List<BazaarHistoryPoint>> normal(BazaarItemInfoRange range) {
            return request(this.historyRequests, range);
        }

        CompletableFuture<List<BazaarHistoryPoint>> refresh(BazaarItemInfoRange range) {
            return request(this.refreshRequests, range);
        }

        private static CompletableFuture<List<BazaarHistoryPoint>> request(
            List<Request> requests,
            BazaarItemInfoRange range
        ) {
            return requests.stream()
                .filter(request -> request.range().equals(range.sdkRange()))
                .filter(request -> !request.future().isDone())
                .findFirst()
                .orElseThrow()
                .future();
        }

        @Override
        public void close() {}
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(this.instant, zone);
        }

        @Override
        public Instant instant() {
            return this.instant;
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            this.tasks.add(command);
        }

        void runAll() {
            while (!this.tasks.isEmpty()) {
                this.tasks.removeFirst().run();
            }
        }
    }
}
