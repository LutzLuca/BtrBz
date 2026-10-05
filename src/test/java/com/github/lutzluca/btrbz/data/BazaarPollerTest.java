package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.BazaarPoller.MarketReply;
import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.exceptions.BadStatusCodeException;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarPollerTest {
    private static final Map<String, SkyBlockBazaarReply.Product> PRODUCTS = Map.of("TEST",
        new Gson().fromJson("{}", SkyBlockBazaarReply.Product.class));
    private final ManualScheduler scheduler = new ManualScheduler();
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final RecordingApi api = new RecordingApi();
    private final List<MarketReply> delivered = new ArrayList<>();
    private final List<String> health = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final BazaarPoller poller = new BazaarPoller(this.delivered::add,
        () -> this.health.add("unavailable"), () -> this.warnings.add("failed"), () -> this.warnings.add("frozen"),
        this.api, this.scheduler, this.clientTasks::add, () -> this.scheduler.now, () -> {});

    @AfterEach
    void close() {
        this.poller.close();
        this.scheduler.runPending();
    }

    @Test
    void workerPublishesUsableUnchangedRepliesToRecoveryOnTheClient() {
        this.poller.start();
        Assertions.assertTrue(this.api.requests.isEmpty());
        this.scheduler.runPending();
        long source = this.scheduler.now;
        this.api.requests.getLast().complete(new Reply(source));
        Assertions.assertTrue(this.clientTasks.isEmpty());
        this.scheduler.runPending();
        Assertions.assertTrue(this.delivered.isEmpty());
        this.drainClient();
        this.assertNextDelay(20_000, 21_000);

        this.reply(this.nextFetch(), new Reply(source));
        this.drainClient();

        this.assertNextDelay(20_000, 21_000);
        Assertions.assertEquals(2, this.delivered.size());
        Assertions.assertTrue(this.delivered.getFirst().advanced());
        // A hibernating runtime can recover even without a newer source publication.
        Assertions.assertFalse(this.delivered.getLast().advanced());
        Assertions.assertTrue(this.health.isEmpty());
    }

    @Test
    void failuresBackOffToTheCeilingKeepRetryingAndResetAfterSuccess() {
        this.api.nextError = new RejectedExecutionException("aborted HTTP worker still exiting");
        this.startFetch();
        this.assertNextDelay(1_000, 1_200);
        this.fail(this.nextFetch(), new IllegalStateException("offline"));
        this.assertNextDelay(2_000, 2_400);
        this.fail(this.nextFetch(), new IllegalStateException("offline"));
        this.assertNextDelay(4_000, 5_000);
        this.drainClient();
        Assertions.assertTrue(this.health.isEmpty());

        for (int attempt = 0; attempt < 4; attempt++) {
            this.fail(this.nextFetch(), new IllegalStateException("offline"));
        }
        long failedAt = this.scheduler.now;
        var request = this.nextFetch();
        Assertions.assertEquals(60_000, this.scheduler.now - failedAt);
        this.drainClient();
        Assertions.assertEquals(List.of("unavailable"), this.health);
        this.fail(request, new IllegalStateException("still offline"));
        failedAt = this.scheduler.now;
        request = this.nextFetch();
        Assertions.assertEquals(60_000, this.scheduler.now - failedAt);

        this.reply(request, new Reply(this.scheduler.now));
        this.fail(this.nextFetch(), new IllegalStateException("new outage"));

        this.assertNextDelay(1_000, 1_200);
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
    void unchangedRepliesDoNotExtendSourceExpiryWhileTheNextRequestIsStalled() {
        long source = this.scheduler.now - 60_000;
        this.reply(this.startFetch(), new Reply(source));
        this.drainClient();
        this.reply(this.nextFetch(), new Reply(source));
        this.drainClient();
        var stalled = this.nextFetch();

        this.scheduler.advanceTo(source + 119_999);
        this.drainClient();
        Assertions.assertTrue(this.health.isEmpty());
        this.scheduler.advanceTo(source + 120_000);
        this.drainClient();

        Assertions.assertEquals(List.of("unavailable"), this.health);
        Assertions.assertFalse(stalled.isDone());
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
    void emptyMarketReplyRetriesWithoutBeingDeliveredAsRecovery() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now, Map.of()));
        this.assertNextDelay(1_000, 1_200);
        this.drainClient();
        Assertions.assertTrue(this.delivered.isEmpty());

        this.reply(this.nextFetch(), new Reply(this.scheduler.now));
        this.drainClient();

        Assertions.assertEquals(1, this.delivered.size());
        Assertions.assertTrue(this.health.isEmpty());
    }

    @Test
    void failureWarningSuppressesFrozenWarningsAndRearmsForANewOutage() {
        this.reply(this.startFetch(), new Reply(this.scheduler.now));
        this.drainClient();
        this.fail(this.nextFetch(), new IllegalStateException("offline"));
        long start = this.scheduler.now;
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
    void sourceAdvancementAndFailuresKeepFrozenWarningsOncePerSource() {
        this.scheduler.advanceBy(600_000);
        long first = this.scheduler.now;
        this.reply(this.startFetch(), new Reply(first));
        this.reply(this.nextFetch(), new Reply(first));
        var request = this.nextFetch();
        this.scheduler.advanceTo(first + 100_000);
        long advanced = this.scheduler.now;
        this.reply(request, new Reply(advanced));
        this.scheduler.advanceTo(first + 300_000);
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceTo(advanced + 299_999);
        this.drainClient();
        Assertions.assertTrue(this.warnings.isEmpty());
        this.scheduler.advanceTo(advanced + 300_000);
        this.drainClient();
        Assertions.assertEquals(List.of("frozen"), this.warnings);
        this.reply(this.api.requests.getLast(), new Reply(advanced));
        this.scheduler.advanceBy(100_000);
        this.drainClient();
        Assertions.assertEquals(1, this.warnings.size());

        long next = this.scheduler.now;
        this.reply(this.api.requests.getLast(), new Reply(next));
        this.drainClient();
        this.fail(this.nextFetch(), new IllegalStateException("brief failure on the new source"));
        this.reply(this.nextFetch(), new Reply(next));
        this.scheduler.advanceTo(next + 300_000);
        this.drainClient();

        Assertions.assertEquals(List.of("frozen", "frozen"), this.warnings);
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
        // Both expiry observations are valid. The lifecycle owner makes suspension and messaging idempotent.
        Assertions.assertEquals(List.of("unavailable", "unavailable"), this.health);
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

    private static final class Reply extends SkyBlockBazaarReply implements SkyBlockBazaarReplyAccessor {
        private final long sourceTime;
        private final Map<String, Product> products;

        private Reply(long sourceTime) {
            this(sourceTime, BazaarPollerTest.PRODUCTS);
        }

        private Reply(long sourceTime, Map<String, Product> products) {
            this.sourceTime = sourceTime;
            this.products = products;
            this.success = true;
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

        private RecordingApi() {
            super(new StubHttpClient());
        }

        @Override
        public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
            if (this.nextError != null) {
                var error = this.nextError;
                this.nextError = null;
                throw error;
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

    private static final class StubHttpClient implements HypixelHttpClient {
        @Override
        public CompletableFuture<HypixelHttpResponse> makeRequest(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<HypixelHttpResponse> makeAuthenticatedRequest(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {}
    }
}
