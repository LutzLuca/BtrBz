package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BazaarPollerTest {
    private final ManualScheduler scheduler = new ManualScheduler();
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final RecordingHttpClient httpClient = new RecordingHttpClient();
    private final RecordingApi api = new RecordingApi(this.httpClient);
    private final List<Map<String, SkyBlockBazaarReply.Product>> delivered = new ArrayList<>();
    private final BazaarPoller poller = new BazaarPoller(
        this.delivered::add, this.api, this.scheduler, this.clientTasks::add);

    @AfterEach
    void close() {
        this.poller.close();
        this.scheduler.runPending();
    }

    private CompletableFuture<SkyBlockBazaarReply> startFetch() {
        this.poller.start();
        this.scheduler.runPending();
        return this.api.requests.getLast();
    }

    private void reply(CompletableFuture<SkyBlockBazaarReply> request, long timestamp) {
        request.complete(new Reply(timestamp));
        this.scheduler.runPending();
    }

    @Nested
    @DisplayName("worker lifecycle")
    class Lifecycle {
        @Test
        void usesARealWorkerAndOnlyHandsDeliveryBackToTheClient() throws Exception {
            var caller = Thread.currentThread();
            var requestThread = new CompletableFuture<Thread>();
            var deliveryTask = new CompletableFuture<Runnable>();
            var deliveryThread = new CompletableFuture<Thread>();
            var httpClosed = new CompletableFuture<Thread>();
            var api = new HypixelAPI(BazaarPollerTest.this.httpClient) {
                @Override
                public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                    requestThread.complete(Thread.currentThread());
                    return CompletableFuture.completedFuture(new Reply(100));
                }

                @Override
                public void shutdown() {
                    httpClosed.complete(Thread.currentThread());
                }
            };
            var worker = Executors.newSingleThreadScheduledExecutor();
            var poller = new BazaarPoller(
                _ -> deliveryThread.complete(Thread.currentThread()), api, worker, deliveryTask::complete);
            try {
                poller.start();
                Assertions.assertNotSame(caller, requestThread.get(5, TimeUnit.SECONDS));
                var deliver = deliveryTask.get(5, TimeUnit.SECONDS);
                Assertions.assertFalse(deliveryThread.isDone());
                deliver.run();
                Assertions.assertSame(caller, deliveryThread.get(5, TimeUnit.SECONDS));
            } finally {
                poller.close();
                Assertions.assertNotSame(caller, httpClosed.get(5, TimeUnit.SECONDS));
                Assertions.assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
        }

        @Test
        void startsIdleAndRepeatedStartsOnlyFetchOnce() {
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.pending.isEmpty());
            BazaarPollerTest.this.poller.start();
            BazaarPollerTest.this.poller.start();
            Assertions.assertTrue(BazaarPollerTest.this.api.requests.isEmpty());
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertEquals(1, BazaarPollerTest.this.api.requests.size());
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
        }

        @Test
        void stopCancelsTheRequestOnTheWorkerWithoutRetrying() {
            var request = BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(request.isCancelled());
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.tasks.isEmpty());
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
        }

        @Test
        void cancelsAScheduledFetch() {
            BazaarPollerTest.this.reply(BazaarPollerTest.this.startFetch(), 100);
            var timer = BazaarPollerTest.this.scheduler.tasks.remove();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(timer.isCancelled());
            timer.run();
            Assertions.assertEquals(1, BazaarPollerTest.this.api.requests.size());
        }

        @Test
        void shutdownCancelsAndClosesOnTheWorkerWithoutAcceptingMoreWork() {
            var request = BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.poller.close();
            Assertions.assertFalse(BazaarPollerTest.this.httpClient.shutdown);
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(request.isCancelled());
            Assertions.assertTrue(BazaarPollerTest.this.httpClient.shutdown);
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.isShutdown());
            Assertions.assertDoesNotThrow(BazaarPollerTest.this.poller::stop);
            Assertions.assertDoesNotThrow(BazaarPollerTest.this.poller::start);
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.pending.isEmpty());
        }

        @Test
        void completionAfterShutdownCannotPublishOrReschedule() {
            var late = new UncancellableRequest();
            BazaarPollerTest.this.api.nextRequest = late;
            BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.poller.close();
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertDoesNotThrow(() -> late.complete(new Reply(100)));
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.pending.isEmpty());
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.tasks.isEmpty());
        }
    }

    @Nested
    @DisplayName("obsolete work")
    class ObsoleteWork {
        @Test
        void stopBeforeTheWorkerStartsDoesNotFetch() {
            BazaarPollerTest.this.poller.start();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(BazaarPollerTest.this.api.requests.isEmpty());
        }

        @Test
        void queuedClientDeliveryIsInvalidImmediatelyOnStop() {
            BazaarPollerTest.this.reply(BazaarPollerTest.this.startFetch(), 100);
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.poller.start();
            // Worker cancellation has not run yet; the queued result must already be invalid.
            BazaarPollerTest.this.clientTasks.remove().run();
            Assertions.assertTrue(BazaarPollerTest.this.delivered.isEmpty());
            BazaarPollerTest.this.scheduler.runPending();
            BazaarPollerTest.this.reply(BazaarPollerTest.this.api.requests.getLast(), 100);
            BazaarPollerTest.this.clientTasks.remove().run();
            Assertions.assertEquals(1, BazaarPollerTest.this.delivered.size());
        }

        @Test
        void lateReplyCannotAffectTheNewRunEvenIfTheTransportCannotCancel() {
            var late = new UncancellableRequest();
            BazaarPollerTest.this.api.nextRequest = late;
            BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.reply(late, 100);
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.tasks.isEmpty());
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
        }

        @Test
        void lateFailureCannotStartRetriesInTheNewRun() {
            var late = new UncancellableRequest();
            BazaarPollerTest.this.api.nextRequest = late;
            BazaarPollerTest.this.startFetch();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.startFetch();
            late.completeExceptionally(new IllegalStateException("old run"));
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(BazaarPollerTest.this.scheduler.tasks.isEmpty());
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
        }
    }

    @Nested
    @DisplayName("polling cadence and delivery")
    class Cadence {
        @Test
        void processesOnTheWorkerAndOnlyPublishesOnTheClient() {
            var request = BazaarPollerTest.this.startFetch();
            request.complete(new Reply(100));
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertTrue(BazaarPollerTest.this.delivered.isEmpty());
            var timer = BazaarPollerTest.this.scheduler.tasks.remove();
            Assertions.assertTrue(timer.delayMs >= 20_200 && timer.delayMs < 20_400);
            BazaarPollerTest.this.clientTasks.remove().run();
            Assertions.assertEquals(1, BazaarPollerTest.this.delivered.size());
            timer.run();
            BazaarPollerTest.this.reply(BazaarPollerTest.this.api.requests.getLast(), 100);
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
            Assertions.assertEquals(250, BazaarPollerTest.this.scheduler.tasks.element().delayMs);
        }

        @Test
        void retainsErrorBackoffForTheCurrentRun() {
            BazaarPollerTest.this.startFetch().completeExceptionally(new IllegalStateException("API unavailable"));
            BazaarPollerTest.this.scheduler.runPending();
            Assertions.assertEquals(500, BazaarPollerTest.this.scheduler.tasks.element().delayMs);
            Assertions.assertTrue(BazaarPollerTest.this.clientTasks.isEmpty());
        }

        @Test
        void restartingRefreshesEvenWhenTheTimestampIsUnchanged() {
            BazaarPollerTest.this.reply(BazaarPollerTest.this.startFetch(), 100);
            BazaarPollerTest.this.clientTasks.remove().run();
            BazaarPollerTest.this.poller.stop();
            BazaarPollerTest.this.reply(BazaarPollerTest.this.startFetch(), 100);
            BazaarPollerTest.this.clientTasks.remove().run();
            Assertions.assertEquals(2, BazaarPollerTest.this.delivered.size());
        }
    }

    private static final class RecordingApi extends HypixelAPI {
        private final List<CompletableFuture<SkyBlockBazaarReply>> requests = new ArrayList<>();
        private CompletableFuture<SkyBlockBazaarReply> nextRequest;

        private RecordingApi(HypixelHttpClient httpClient) {
            super(httpClient);
        }

        @Override
        public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
            var request = this.nextRequest == null ? new CompletableFuture<SkyBlockBazaarReply>() : this.nextRequest;
            this.nextRequest = null;
            this.requests.add(request);
            return request;
        }
    }

    private static final class Reply extends SkyBlockBazaarReply implements SkyBlockBazaarReplyAccessor {
        private final long timestamp;

        private Reply(long timestamp) {
            this.timestamp = timestamp;
            this.success = true;
        }

        @Override
        public long getLastUpdated() {
            return this.timestamp;
        }

        @Override
        public Map<String, Product> getProducts() {
            return Map.of();
        }
    }

    private static final class UncancellableRequest extends CompletableFuture<SkyBlockBazaarReply> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        private final Queue<ScheduledTask> tasks = new ArrayDeque<>();
        private final Queue<Runnable> pending = new ArrayDeque<>();

        private ManualScheduler() {
            super(1);
        }

        private void runPending() {
            while (!this.pending.isEmpty()) {
                this.pending.remove().run();
            }
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
            var scheduled = new ScheduledTask(task, unit.toMillis(delay));
            this.tasks.add(scheduled);
            return scheduled;
        }
    }

    private static final class ScheduledTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        private final long delayMs;

        private ScheduledTask(Runnable task, long delayMs) {
            super(task, null);
            this.delayMs = delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.delayMs, TimeUnit.MILLISECONDS);
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
