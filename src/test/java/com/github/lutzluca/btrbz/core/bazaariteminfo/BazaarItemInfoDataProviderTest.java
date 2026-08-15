package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.CurrentPrices;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.History;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Loading;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Success;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import com.github.lutzluca.coflnet.BazaarSnapshot;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import com.github.lutzluca.coflnet.HistoryRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarItemInfoDataProviderTest {
    @Test
    void mapsSnapshotAndSortsHistory() {
        var client = new FakeClient();
        var provider = new BazaarItemInfoDataProvider(client);

        provider.load("ENCHANTED_DIAMOND", HistoryRange.Preset.DAY);
        client.snapshotRequests.getFirst().complete(Optional.of(snapshot(12.34, 11.26)));
        client.historyRequests.getFirst().complete(List.of(point(3), point(1), point(2)));

        var state = provider.state().orElseThrow();
        assertInstanceOf(Success.class, state.currentPrices());
        var current = ((Success<CurrentPrices>) state.currentPrices()).value();
        assertEquals("12.3", current.buyText());
        assertEquals("11.3", current.sellText());

        assertInstanceOf(Success.class, state.history());
        var history = ((Success<History>) state.history()).value();
        assertEquals(
            List.of(1L, 2L, 3L),
            history.points().stream().map(point -> point.timestamp().getEpochSecond()).toList());
    }

    @Test
    void ignoresCompletionsFromAnOlderRequest() {
        var client = new FakeClient();
        var provider = new BazaarItemInfoDataProvider(client);

        provider.load("FIRST", HistoryRange.Preset.HOUR);
        provider.load("SECOND", HistoryRange.Preset.WEEK);

        client.snapshotRequests.getFirst().complete(Optional.of(snapshot(1, 2)));
        client.historyRequests.getFirst().complete(List.of(point(1)));

        var state = provider.state().orElseThrow();
        assertEquals("SECOND", state.itemTag());
        assertInstanceOf(Loading.class, state.currentPrices());
        assertInstanceOf(Loading.class, state.history());
    }

    @Test
    void retainsSameRequestDataWhileRefreshingAndAfterFailure() {
        var client = new FakeClient();
        var provider = new BazaarItemInfoDataProvider(client);

        provider.load("ITEM", HistoryRange.Preset.DAY);
        client.snapshotRequests.getFirst().complete(Optional.of(snapshot(7, 8)));
        client.historyRequests.getFirst().complete(List.of(point(1)));

        provider.load("ITEM", HistoryRange.Preset.DAY);
        var loading = provider.state().orElseThrow();
        assertTrue(assertInstanceOf(Loading.class, loading.currentPrices()).previous().isPresent());
        assertTrue(assertInstanceOf(Loading.class, loading.history()).previous().isPresent());

        client.snapshotRequests.get(1).completeExceptionally(new IllegalStateException("offline"));
        var failed = provider.state().orElseThrow();
        var failure = assertInstanceOf(Failure.class, failed.currentPrices());
        assertEquals("offline", failure.message());
        assertTrue(failure.previous().isPresent());
    }

    @Test
    void changingRangeRetainsSnapshotButNotOldHistory() {
        var client = new FakeClient();
        var provider = new BazaarItemInfoDataProvider(client);

        provider.load("ITEM", HistoryRange.Preset.HOUR);
        client.snapshotRequests.getFirst().complete(Optional.of(snapshot(7, 8)));
        client.historyRequests.getFirst().complete(List.of(point(1)));

        provider.load("ITEM", HistoryRange.Preset.WEEK);
        var loading = provider.state().orElseThrow();
        assertTrue(assertInstanceOf(Loading.class, loading.currentPrices()).previous().isPresent());
        assertTrue(assertInstanceOf(Loading.class, loading.history()).previous().isEmpty());
    }

    @Test
    void serializesConcurrentStateNotifications() throws Exception {
        var client = new FakeClient();
        var delivered = new CopyOnWriteArrayList<BazaarItemInfoViewData.ScreenState>();
        var partialListenerEntered = new CountDownLatch(1);
        var releasePartialListener = new CountDownLatch(1);
        var provider = new BazaarItemInfoDataProvider(client, state -> {
            if (state.currentPrices() instanceof Success<?> && state.history() instanceof Loading<?>) {
                partialListenerEntered.countDown();
                try {
                    assertTrue(releasePartialListener.await(3, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            delivered.add(state);
        });

        provider.load("ITEM", HistoryRange.Preset.DAY);
        var snapshotThread = new Thread(() -> client.snapshotRequests.getFirst().complete(Optional.of(snapshot(7, 8))));
        snapshotThread.start();
        assertTrue(partialListenerEntered.await(3, TimeUnit.SECONDS));

        var historyThread = new Thread(() -> client.historyRequests.getFirst().complete(List.of(point(1))));
        historyThread.start();
        releasePartialListener.countDown();
        snapshotThread.join(3_000);
        historyThread.join(3_000);

        var last = delivered.getLast();
        assertInstanceOf(Success.class, last.currentPrices());
        assertInstanceOf(Success.class, last.history());
    }

    private static BazaarSnapshot snapshot(double buy, double sell) {
        return new BazaarSnapshot(
            "ITEM", buy, 1, 2, 3, sell, 4, 5, 6,
            Instant.ofEpochSecond(100), List.of(), List.of());
    }

    private static BazaarHistoryPoint point(long epochSecond) {
        return new BazaarHistoryPoint(
            epochSecond, epochSecond + 1,
            null, null, null, null,
            0, 0, 0, 0, Instant.ofEpochSecond(epochSecond));
    }

    private static final class FakeClient implements CoflnetBazaarClient {
        private final List<CompletableFuture<Optional<BazaarSnapshot>>> snapshotRequests = new ArrayList<>();
        private final List<CompletableFuture<List<BazaarHistoryPoint>>> historyRequests = new ArrayList<>();

        @Override
        public CompletionStage<Optional<BazaarSnapshot>> snapshot(String itemTag) {
            var request = new CompletableFuture<Optional<BazaarSnapshot>>();
            this.snapshotRequests.add(request);
            return request;
        }

        @Override
        public CompletionStage<List<BazaarHistoryPoint>> history(String itemTag, HistoryRange range) {
            var request = new CompletableFuture<List<BazaarHistoryPoint>>();
            this.historyRequests.add(request);
            return request;
        }

        @Override
        public void close() {}
    }
}
