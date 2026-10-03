package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.FeatureRuntime;
import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertConfig;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarPoller.MarketReply;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.exceptions.BadStatusCodeException;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarPollerTest {
    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("Test Product", "TEST", null);
    private final ManualScheduler scheduler = new ManualScheduler();
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final RecordingHttpClient http = new RecordingHttpClient();
    private final RecordingApi api = new RecordingApi(this.http);
    private final List<MarketReply> delivered = new ArrayList<>();
    private final List<String> health = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private Consumer<MarketReply> consumer = this.delivered::add;
    private Runnable unavailable = () -> this.health.add("unavailable");
    private int aborts;
    private TrackedOrderManager integrationOrders;
    private final BazaarPoller poller = new BazaarPoller(reply -> this.consumer.accept(reply),
        () -> this.unavailable.run(), () -> this.warnings.add("failed"), () -> this.warnings.add("frozen"),
        this.api, this.scheduler, this.clientTasks::add, () -> this.scheduler.now, () -> this.aborts++);

    @AfterEach
    void close() {
        this.poller.close();
        this.scheduler.runPending();
    }

    @Test
    void startsIdleAndRepeatedStartsOnlyFetchOnce() {
        this.poller.start();
        this.poller.start();
        Assertions.assertTrue(this.api.requests.isEmpty());
        this.scheduler.runPending();
        Assertions.assertEquals(1, this.api.requests.size());
        Assertions.assertTrue(this.clientTasks.isEmpty());
    }

    @Test
    void usesARealWorkerAndOnlyDeliversOnTheClient() throws Exception {
        var caller = Thread.currentThread();
        var requestThread = new CompletableFuture<Thread>();
        var task = new CompletableFuture<Runnable>();
        var deliveryThread = new CompletableFuture<Thread>();
        var closedThread = new CompletableFuture<Thread>();
        long now = System.currentTimeMillis();
        var api = new HypixelAPI(this.http) {
            @Override
            public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                requestThread.complete(Thread.currentThread());
                return CompletableFuture.completedFuture(new Reply(now));
            }

            @Override
            public void shutdown() {
                closedThread.complete(Thread.currentThread());
            }
        };
        var worker = Executors.newSingleThreadScheduledExecutor();
        var poller = new BazaarPoller(_ -> deliveryThread.complete(Thread.currentThread()), () -> {}, () -> {},
            () -> {}, api, worker, task::complete, () -> now, () -> {});
        try {
            poller.start();
            Assertions.assertNotSame(caller, requestThread.get(5, TimeUnit.SECONDS));
            var delivery = task.get(5, TimeUnit.SECONDS);
            Assertions.assertFalse(deliveryThread.isDone());
            delivery.run();
            Assertions.assertSame(caller, deliveryThread.get(5, TimeUnit.SECONDS));
        } finally {
            poller.close();
            Assertions.assertNotSame(caller, closedThread.get(5, TimeUnit.SECONDS));
            Assertions.assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void unchangedSuccessUsesNormalCadenceWithoutClaimingPublication() {
        long source = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(source));
        this.drainClient();
        this.assertNextDelay(20_200, 20_399);
        this.reply(this.nextFetch(), new Reply(source));
        this.drainClient();
        this.assertNextDelay(20_200, 20_399);
        Assertions.assertEquals(2, this.delivered.size());
        Assertions.assertTrue(this.delivered.getFirst().advanced());
        Assertions.assertFalse(this.delivered.getLast().advanced());
        Assertions.assertTrue(this.health.isEmpty());
    }

    @Test
    void transientFailureKeepsLastSnapshotAndSuccessResetsBackoff() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.drainClient();
        this.fail(this.nextFetch(), new IllegalStateException("transient"));
        this.drainClient();
        this.assertNextDelay(1_000, 1_199);
        Assertions.assertTrue(this.health.isEmpty());
        this.reply(this.nextFetch(), new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new IllegalStateException("another episode"));
        this.assertNextDelay(1_000, 1_199);
        Assertions.assertTrue(this.health.isEmpty());
    }

    @Test
    void failuresRetryIndefinitelyWithBoundedExponentialBackoff() {
        var request = this.startFetch();
        for (int attempt = 0; attempt < 12; attempt++) {
            this.fail(request, new IllegalStateException("unavailable"));
            long base = Math.min(60_000, 1_000L << Math.min(attempt, 6));
            // Health timers may precede the next retry after several failures.
            long retry = this.scheduler.liveTasks().stream()
                .mapToLong(task -> task.deadline - this.scheduler.now)
                .filter(delay -> delay >= base && delay <= Math.min(60_000, base + base / 5 - 1))
                .findFirst().orElseThrow();
            Assertions.assertTrue(retry <= 60_000);
            request = this.nextFetch();
        }
        Assertions.assertEquals(13, this.api.requests.size());
    }

    @Test
    void throttlingUsesCeilingAndServiceFailuresUseAtLeastFiveSeconds() {
        this.fail(this.startFetch(), new CompletionException(new BadStatusCodeException(429, "throttled")));
        this.assertNextDelay(60_000, 60_000);
        this.reply(this.nextFetch(), new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new BadStatusCodeException(503, "maintenance"));
        this.assertNextDelay(5_000, 5_000);
    }

    @Test
    void failureGraceExpiresExactlyAtTwoMinutesEvenWithRequestInFlight() {
        long start = this.scheduler.now;
        this.fail(this.startFetch(), new IllegalStateException("offline"));
        this.scheduler.advanceTo(start + 119_999);
        this.drainClient();
        Assertions.assertTrue(this.health.isEmpty());
        Assertions.assertFalse(this.api.requests.getLast().isDone());
        this.scheduler.advanceTo(start + 120_000);
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
        this.scheduler.advanceTo(start + 240_000);
        this.drainClient();
        Assertions.assertEquals(1, this.health.size());
    }

    @Test
    void blockedNativeDnsDeadlineAllowsFailureRetriesHealthAndWarningClocksToProgress() throws Exception {
        var dns = new BoundedBazaarHttpClientTest.BlockedDns();
        var transport = new BoundedBazaarHttpClient(1_000, 3_000, 200, dns);
        var failed = new CountDownLatch(1);
        var api = new HypixelAPI(transport) {
            @Override
            public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                CompletableFuture<SkyBlockBazaarReply> stage = transport.makeRequest("http://bazaar.test:9/bazaar")
                    .thenApply(_ -> new Reply(BazaarPollerTest.this.scheduler.now));
                stage.whenComplete((_, _) -> failed.countDown());
                return stage;
            }
        };
        var poller = new BazaarPoller(this.delivered::add, () -> this.health.add("unavailable"),
            () -> this.warnings.add("failed"), () -> this.warnings.add("frozen"), api, this.scheduler,
            this.clientTasks::add, () -> this.scheduler.now, transport::abortCurrentRequest);
        try {
            poller.start();
            this.scheduler.runPending();
            Assertions.assertTrue(dns.entered.await(2, TimeUnit.SECONDS));
            Assertions.assertTrue(failed.await(2, TimeUnit.SECONDS));
            this.scheduler.runPending();
            this.assertNextDelay(1_000, 1_199);
            long start = this.scheduler.now;
            this.scheduler.advanceTo(start + 119_999);
            this.drainClient();
            Assertions.assertTrue(this.health.isEmpty());
            this.scheduler.advanceTo(start + 120_000);
            this.drainClient();
            Assertions.assertEquals(List.of("unavailable"), this.health);
            this.scheduler.advanceTo(start + 300_000);
            this.drainClient();
            Assertions.assertEquals(List.of("failed"), this.warnings);
            Assertions.assertThrows(RejectedExecutionException.class,
                () -> transport.makeRequest("http://bazaar.test:9/bazaar"));
            Assertions.assertTrue(this.delivered.isEmpty());
        } finally {
            poller.close();
            this.scheduler.runPending();
            dns.release.countDown();
            transport.shutdown();
        }
    }

    @Test
    void structurallyValidOldSuccessEndsFailureEpisodeWithoutRecovery() {
        this.fail(this.startFetch(), new IllegalStateException("offline"));
        var request = this.nextFetch();
        this.scheduler.advanceBy(118_000);
        this.reply(request, new Reply(this.scheduler.now - 120_000));
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
        this.scheduler.advanceBy(300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen"), this.warnings);
        Assertions.assertTrue(this.delivered.isEmpty());
    }

    @Test
    void installedSourceExpiresWithoutWaitingForTheNextRequest() {
        long source = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(source));
        this.drainClient();
        this.nextFetch();
        this.scheduler.advanceTo(source + 119_999);
        this.drainClient();
        Assertions.assertTrue(this.health.isEmpty());
        this.scheduler.advanceTo(source + 120_000);
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
        Assertions.assertFalse(this.api.requests.getLast().isDone());
    }

    @Test
    void unchangedRepliesDoNotExtendSourceAge() {
        long source = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(source));
        for (int attempt = 0; attempt < 5; attempt++) {
            this.reply(this.nextFetch(), new Reply(source));
            this.drainClient();
        }
        this.scheduler.advanceTo(source + 120_000);
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
        Assertions.assertEquals(6, this.delivered.size());
    }

    @Test
    void oldFirstReplyCannotRecoverAndOlderRepliesCannotReplaceNewerSource() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now - 120_000));
        this.drainClient();
        Assertions.assertTrue(this.delivered.isEmpty());
        Assertions.assertEquals(List.of("unavailable"), this.health);
        var request = this.nextFetch();
        long source = this.scheduler.now;
        this.reply(request, new Reply(source));
        this.drainClient();
        this.reply(this.nextFetch(), new Reply(source - 1));
        this.drainClient();
        Assertions.assertEquals(1, this.delivered.size());
        this.scheduler.advanceTo(source + 120_000);
        this.drainClient();
        Assertions.assertEquals(2, this.health.size());
    }

    @Test
    void structurallyInvalidRepliesRetryWithoutRecovery() {
        var nullProduct = new HashMap<String, SkyBlockBazaarReply.Product>();
        nullProduct.put("TEST", null);
        var unsuccessful = new Reply(this.scheduler.now);
        unsuccessful.successful(false);
        var missingAccessor = new Gson().fromJson("{\"success\":true}", SkyBlockBazaarReply.class);
        SkyBlockBazaarReply[] invalid = {null, unsuccessful, missingAccessor, new Reply(0),
            new Reply(this.scheduler.now, null), new Reply(this.scheduler.now, Map.of()),
            new Reply(this.scheduler.now, nullProduct)};
        var request = this.startFetch();
        for (var reply : invalid) {
            this.reply(request, reply);
            request = this.nextFetch();
        }
        Assertions.assertTrue(this.delivered.isEmpty());
        this.scheduler.advanceBy(120_000);
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
    }

    @Test
    void synchronousRequestFailureAndNullRequestUseNormalRetryPolicy() {
        this.api.nextError = new RejectedExecutionException("worker still exiting");
        this.startFetch();
        this.assertNextDelay(1_000, 1_199);
        this.api.nullRequest = true;
        this.nextFetch();
        this.assertNextDelay(2_000, 2_399);
        Assertions.assertTrue(this.clientTasks.isEmpty());
    }

    @Test
    void failureWarningIsOncePerEpisodeAndRearmsAfterStructuralSuccess() {
        long start = this.scheduler.now;
        this.fail(this.startFetch(), new IllegalStateException("offline"));
        this.scheduler.advanceTo(start + 299_999);
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceTo(start + 300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("failed"), this.warnings);
        this.fail(this.api.requests.getLast(), new IllegalStateException("still offline"));
        this.scheduler.advanceBy(100_000);
        this.drainClient();
        Assertions.assertEquals(1, this.warnings.size());
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new IllegalStateException("new outage"));
        this.scheduler.advanceBy(300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("failed", "failed"), this.warnings);
    }

    @Test
    void frozenWarningStartsOnFirstSuccessAndRearmsExactlyAfterAdvancement() {
        this.scheduler.advanceBy(600_000);
        long first = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(first));
        while (this.scheduler.now < first + 280_000) {
            this.reply(this.nextFetch(), new Reply(first));
        }
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceTo(first + 300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen"), this.warnings);
        var request = this.nextFetch();
        long next = this.scheduler.now;
        this.reply(request, new Reply(next));
        this.scheduler.advanceTo(next + 299_999);
        this.drainClient();
        Assertions.assertEquals(1, this.warnings.size());
        this.scheduler.advanceTo(next + 300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen", "frozen"), this.warnings);
    }

    @Test
    void advancingBeforeOldFrozenTimerFiresRearmsFromNewPublication() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.nextFetch();
        this.scheduler.advanceBy(100_000);
        long advanced = this.scheduler.now;
        this.reply(this.api.requests.getLast(), new Reply(advanced));
        this.scheduler.advanceTo(advanced + 299_999);
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceTo(advanced + 300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen"), this.warnings);
    }

    @Test
    void failedRequestsDoNotAlsoWarnAboutFrozenSuccessfulReplies() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new IllegalStateException("offline"));
        this.scheduler.advanceBy(400_000);
        this.drainClient();
        Assertions.assertEquals(List.of("failed"), this.warnings);
    }

    @Test
    void queuedFailureWarningCannotSurviveEndingAndReenteringSameRunEpisode() {
        this.fail(this.startFetch(), new IllegalStateException("offline"));
        this.scheduler.advanceBy(300_000);
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new IllegalStateException("new episode"));
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceBy(300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("failed"), this.warnings);
    }

    @Test
    void queuedFrozenWarningDropsAfterNewSourceAndFailedRequestEpisode() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.scheduler.advanceBy(300_000);
        long next = this.scheduler.now;
        this.reply(this.api.requests.getLast(), new Reply(next));
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceBy(300_000);
        this.fail(this.api.requests.getLast(), new IllegalStateException("offline"));
        this.reply(this.nextFetch(), new Reply(next));
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceBy(0);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen"), this.warnings);
    }

    @Test
    void clientQueuedCandidateThatExpiresCannotReactivate() {
        long source = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(source));
        // The client is delayed independently of worker timers.
        this.scheduler.now = source + 120_000;
        this.drainClient();
        Assertions.assertTrue(this.delivered.isEmpty());
        Assertions.assertEquals(List.of("unavailable"), this.health);
        this.scheduler.advanceBy(0);
        this.drainClient();
        Assertions.assertEquals(1, this.health.size());
    }

    @Test
    void obsoleteCandidateAndQueuedHibernateDropAfterNewUsableSource() {
        long source = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(source));
        this.scheduler.advanceTo(source + 120_000);
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.drainClient();
        Assertions.assertEquals(1, this.delivered.size());
        Assertions.assertTrue(this.health.isEmpty());
    }

    @Test
    void stopInvalidatesDataHealthAndWarningsBeforeWorkerCleanupOrRestart() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.scheduler.advanceBy(300_000);
        Assertions.assertFalse(this.clientTasks.isEmpty());
        this.poller.stop();
        this.poller.start();
        this.drainClient();
        Assertions.assertTrue(this.delivered.isEmpty());
        Assertions.assertTrue(this.health.isEmpty());
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.runPending();
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.drainClient();
        Assertions.assertEquals(1, this.delivered.size());
    }

    @Test
    void lateUncancellableCompletionCannotAffectNewRunOrScheduleRetries() {
        var late = new UncancellableRequest();
        this.api.nextRequest = late;
        this.startFetch();
        this.poller.stop();
        var oldFailure = new UncancellableRequest();
        this.api.nextRequest = oldFailure;
        this.startFetch();
        this.reply(late, new Reply(this.scheduler.now));
        Assertions.assertTrue(this.scheduler.liveTasks().isEmpty());
        Assertions.assertTrue(this.clientTasks.isEmpty());
        this.poller.stop();
        this.startFetch();
        Assertions.assertTrue(oldFailure.completeExceptionally(new IllegalStateException("obsolete")));
        this.scheduler.runPending();
        Assertions.assertTrue(this.scheduler.liveTasks().isEmpty());
        this.drainClient();
        Assertions.assertTrue(this.delivered.isEmpty());
        Assertions.assertTrue(this.health.isEmpty());
        Assertions.assertTrue(this.warnings.isEmpty());
    }

    @Test
    void stopBeforeWorkerStartsDoesNotFetchAndStopCancelsRequestImmediately() {
        this.poller.start();
        this.poller.stop();
        this.scheduler.runPending();
        Assertions.assertTrue(this.api.requests.isEmpty());
        var request = this.startFetch();
        this.poller.stop();
        Assertions.assertEquals(2, this.aborts);
        this.scheduler.runPending();
        Assertions.assertTrue(request.isCancelled());
        Assertions.assertTrue(this.scheduler.liveTasks().isEmpty());
    }

    @Test
    void closeShutsDownTransportWithoutAcceptingLateWork() {
        var late = new UncancellableRequest();
        this.api.nextRequest = late;
        this.startFetch();
        this.poller.close();
        Assertions.assertEquals(1, this.aborts);
        Assertions.assertFalse(this.http.shutdown);
        this.scheduler.runPending();
        Assertions.assertTrue(this.http.shutdown);
        Assertions.assertTrue(this.scheduler.isShutdown());
        this.reply(late, new Reply(this.scheduler.now));
        Assertions.assertTrue(this.clientTasks.isEmpty());
        Assertions.assertTrue(this.scheduler.pending.isEmpty());
        Assertions.assertDoesNotThrow(this.poller::start);
        Assertions.assertDoesNotThrow(this.poller::stop);
    }

    @Test
    void startupGatePrecedesProducersAndAlwaysActiveOnlyChangesStartup() {
        var always = new AtomicBoolean();
        var runtime = this.runtime(always, new BazaarData());
        runtime.activate();
        Assertions.assertTrue(runtime.isHibernating());
        Assertions.assertEquals(1, this.api.requests.size());
        runtime.activate();
        Assertions.assertTrue(runtime.isHibernating());
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.drainClient();
        Assertions.assertTrue(runtime.isActive());
        runtime.deactivate();
        always.set(true);
        runtime.activate();
        Assertions.assertTrue(runtime.isActive());
        this.fail(this.api.requests.getLast(), new IllegalStateException("offline"));
        this.scheduler.advanceBy(120_000);
        this.drainClient();
        Assertions.assertTrue(runtime.isHibernating());
        runtime.activate();
        Assertions.assertTrue(runtime.isHibernating());
        runtime.deactivate();
        always.set(false);
        runtime.activate();
        Assertions.assertTrue(runtime.isHibernating());
    }

    @Test
    void automaticExpiryRetainsSessionFactsAndQuietRecoveryDeliversAlertAfterGate() {
        var data = new BazaarData();
        var runtime = this.runtime(new AtomicBoolean(false), data);
        var orders = this.integrationOrders;
        int[] unknownAtPublication = {0};
        data.addListener(snapshot -> {
            if (snapshot.available() && orders.currentOrders().stream()
                .anyMatch(order -> order.status() instanceof OrderStatus.Unknown)) {
                unknownAtPublication[0]++;
            }
        });
        var config = new AlertConfig();
        int[] reached = {0};
        var alerts = new AlertManager(data, () -> config, () -> {}, _ -> {
            Assertions.assertTrue(runtime.isActive());
            Assertions.assertTrue(data.hasMarketData());
            Assertions.assertInstanceOf(OrderStatus.Undercut.class, orders.currentOrders().getFirst().status());
            reached[0]++;
        });
        data.addListener(alerts::onBazaarUpdate);
        runtime.activate();
        orders.syncOrders(List.of(order(10, 100, 3, 10), order(20, 90, 4, 11),
            new OrderInfo.FilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, 5, 100, 5, 5, 12)));
        orders.reorder(orders.currentOrders().getFirst().id(), 2);
        var ids = orders.currentOrders().stream().map(TrackedOrderManager.TrackedOrderSnapshot::id).toList();
        long generation = runtime.sessionGeneration();
        long source = this.scheduler.now;
        this.reply(this.api.requests.getLast(), new Reply(source));
        this.drainClient();
        this.nextFetch();
        this.scheduler.advanceTo(source + 120_000);
        this.drainClient();
        Assertions.assertTrue(runtime.isRunning());
        Assertions.assertTrue(runtime.isHibernating());
        Assertions.assertFalse(data.hasMarketData());
        Assertions.assertEquals(ids, orders.currentOrders().stream()
            .map(TrackedOrderManager.TrackedOrderSnapshot::id).toList());
        Assertions.assertEquals(1, orders.filledOrderCount());
        Assertions.assertEquals(3, orders.currentOrders().getLast().fillAmountSnapshot());
        alerts.saveAlert(null, new AlertDefinition(1, new IndexedProduct("TEST", "Test Product"),
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Sell, AlertType.Direction.Above), 105))).get();
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now, products(110)));
        this.drainClient();
        Assertions.assertTrue(runtime.isActive());
        Assertions.assertEquals(generation, runtime.sessionGeneration());
        Assertions.assertEquals(ids, orders.currentOrders().stream()
            .map(TrackedOrderManager.TrackedOrderSnapshot::id).toList());
        Assertions.assertEquals(1, orders.filledOrderCount());
        Assertions.assertEquals(1, reached[0]);
        Assertions.assertEquals(0, unknownAtPublication[0]);
        this.reply(this.nextFetch(), new Reply(this.scheduler.now, products(110)));
        this.drainClient();
        Assertions.assertEquals(1, reached[0]);
    }

    @Test
    void failedQuietBaselineRetriesOnSameTimestampWithoutNewPublication() {
        var data = new BazaarData();
        var runtime = this.runtime(new AtomicBoolean(false), data);
        runtime.activate();
        this.integrationOrders.syncOrders(List.of(order(10, 100, 0, 10), order(20, 90, 0, 11)));
        long source = this.scheduler.now;
        var malformed = new Gson().fromJson("""
            {"products":{"TEST":{"sell_summary":[{"pricePerUnit":100,"orders":1},null]}}}
            """, SkyBlockBazaarReply.class).getProducts();
        this.reply(this.api.requests.getLast(), new Reply(source, malformed));
        this.drainClient();
        Assertions.assertTrue(runtime.isHibernating());
        Assertions.assertFalse(data.hasMarketData());
        this.reply(this.nextFetch(), new Reply(source));
        this.drainClient();
        Assertions.assertTrue(runtime.isActive());
        Assertions.assertTrue(data.hasMarketData());
    }

    @Test
    void manualDisableWhileHibernateRejectsLateRecoveryAndRestartBeginsGated() {
        var data = new BazaarData();
        var runtime = this.runtime(new AtomicBoolean(false), data);
        runtime.activate();
        this.integrationOrders.syncOrders(List.of(order(10, 100, 0, 10)));
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        runtime.deactivate();
        this.drainClient();
        Assertions.assertFalse(runtime.isRunning());
        Assertions.assertFalse(data.hasMarketData());
        Assertions.assertTrue(this.integrationOrders.currentOrders().isEmpty());
        runtime.activate();
        Assertions.assertTrue(runtime.isHibernating());
        this.reply(this.api.requests.getLast(), new Reply(this.scheduler.now));
        this.drainClient();
        Assertions.assertTrue(runtime.isActive());
    }

    private FeatureRuntime runtime(AtomicBoolean always, BazaarData data) {
        var config = new TrackedOrderManager.OrderManagerConfig();
        config.enabled = false;
        this.integrationOrders = new TrackedOrderManager(data, () -> config);
        data.addListener(this.integrationOrders::onBazaarUpdate);
        var owner = new AtomicReference<FeatureRuntime>();
        var runtime = new FeatureRuntime(new Activation(() -> true, always::get, _ -> {}),
            data, this.integrationOrders, () -> {
                Assertions.assertEquals(always.get(), owner.get().isActive());
                Assertions.assertTrue(owner.get().isRunning());
                this.startFetch();
            }, () -> {}, this.poller::stop);
        owner.set(runtime);
        this.consumer = runtime::onMarketReply;
        this.unavailable = runtime::hibernate;
        return runtime;
    }

    private static OrderInfo.UnfilledOrderInfo order(int volume, double price, int fill, int slot) {
        return new OrderInfo.UnfilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, volume, price, fill, 0, slot);
    }

    private CompletableFuture<SkyBlockBazaarReply> startFetch() {
        this.poller.start();
        this.scheduler.runPending();
        return this.api.requests.isEmpty() ? null : this.api.requests.getLast();
    }

    private CompletableFuture<SkyBlockBazaarReply> nextFetch() {
        int count = this.api.requests.size();
        for (int attempt = 0; attempt < 20 && count == this.api.requests.size(); attempt++) {
            this.scheduler.advanceToNext();
        }
        Assertions.assertTrue(this.api.requests.size() > count, "No next request was scheduled");
        return this.api.requests.getLast();
    }

    private void reply(CompletableFuture<SkyBlockBazaarReply> request, SkyBlockBazaarReply reply) {
        request.complete(reply);
        this.scheduler.runPending();
    }

    private void fail(CompletableFuture<SkyBlockBazaarReply> request, Throwable failure) {
        request.completeExceptionally(failure);
        this.scheduler.runPending();
    }

    private void drainClient() {
        while (!this.clientTasks.isEmpty()) {
            this.clientTasks.remove().run();
        }
    }

    private void assertNextDelay(long min, long max) {
        long delay = this.scheduler.liveTasks().getFirst().deadline - this.scheduler.now;
        Assertions.assertTrue(delay >= min && delay <= max, "Unexpected next timer: " + delay);
    }

    static Map<String, SkyBlockBazaarReply.Product> products(double price) {
        return new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":120,"amount":100,"orders":2}]
            }}}
            """.formatted(price), SkyBlockBazaarReply.class).getProducts();
    }

    static final class Reply extends SkyBlockBazaarReply implements SkyBlockBazaarReplyAccessor {
        private final long sourceTime;
        private final Map<String, Product> products;

        Reply(long sourceTime) {
            this(sourceTime, BazaarPollerTest.products(100));
        }

        Reply(long sourceTime, Map<String, Product> products) {
            this.sourceTime = sourceTime;
            this.products = products;
            this.success = true;
        }

        private void successful(boolean successful) {
            this.success = successful;
        }

        @Override
        public long getLastUpdated() {
            return this.sourceTime;
        }

        @Override
        public Map<String, Product> getProducts() {
            return this.products;
        }
    }

    private static final class RecordingApi extends HypixelAPI {
        private final List<CompletableFuture<SkyBlockBazaarReply>> requests = new ArrayList<>();
        private CompletableFuture<SkyBlockBazaarReply> nextRequest;
        private RuntimeException nextError;
        private boolean nullRequest;

        private RecordingApi(HypixelHttpClient http) {
            super(http);
        }

        @Override
        public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
            if (this.nextError != null) {
                var error = this.nextError;
                this.nextError = null;
                throw error;
            }
            if (this.nullRequest) {
                this.nullRequest = false;
                this.requests.add(null);
                return null;
            }
            var request = this.nextRequest == null ? new CompletableFuture<SkyBlockBazaarReply>() : this.nextRequest;
            this.nextRequest = null;
            this.requests.add(request);
            return request;
        }
    }

    private static final class UncancellableRequest extends CompletableFuture<SkyBlockBazaarReply> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        private long now = 1_000_000;
        private long sequence;
        private final Queue<Runnable> pending = new ArrayDeque<>();
        private final PriorityQueue<ScheduledTask> timers = new PriorityQueue<>(Comparator
            .comparingLong((ScheduledTask task) -> task.deadline).thenComparingLong(task -> task.sequence));

        private ManualScheduler() {
            super(1);
        }

        private List<ScheduledTask> liveTasks() {
            return this.timers.stream().filter(task -> !task.isDone())
                .sorted(Comparator.comparingLong(task -> task.deadline)).toList();
        }

        private void runPending() {
            while (!this.pending.isEmpty()) {
                this.pending.remove().run();
            }
        }

        private void advanceBy(long delay) {
            this.advanceTo(this.now + delay);
        }

        private void advanceToNext() {
            var tasks = this.liveTasks();
            Assertions.assertFalse(tasks.isEmpty(), "No scheduled work");
            this.advanceTo(tasks.getFirst().deadline);
        }

        private void advanceTo(long target) {
            this.runPending();
            while (!this.timers.isEmpty() && this.timers.element().deadline <= target) {
                var timer = this.timers.remove();
                if (!timer.isDone()) {
                    this.now = Math.max(this.now, timer.deadline);
                    timer.run();
                    this.runPending();
                }
            }
            this.now = Math.max(this.now, target);
        }

        @Override
        public void execute(Runnable task) {
            if (this.isShutdown()) {
                throw new RejectedExecutionException("scheduler stopped");
            }
            this.pending.add(task);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            if (this.isShutdown()) {
                throw new RejectedExecutionException("scheduler stopped");
            }
            var timer = new ScheduledTask(task, this.now + unit.toMillis(delay), ++this.sequence);
            this.timers.add(timer);
            return timer;
        }
    }

    private static final class ScheduledTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        private final long deadline;
        private final long sequence;

        private ScheduledTask(Runnable task, long deadline, long sequence) {
            super(task, null);
            this.deadline = deadline;
            this.sequence = sequence;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.deadline, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    private static final class RecordingHttpClient implements HypixelHttpClient {
        private boolean shutdown;

        @Override
        public CompletableFuture<HypixelHttpResponse> makeRequest(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<HypixelHttpResponse> makeAuthenticatedRequest(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            this.shutdown = true;
        }
    }
}
