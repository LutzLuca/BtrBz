package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import io.vavr.control.Try;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.exceptions.BadStatusCodeException;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.minecraft.client.Minecraft;

/** Owns polling and health clocks on its worker, with guarded publication on the client thread. */
@Slf4j
public final class BazaarPoller implements AutoCloseable {
    private static final long NORMAL_INTERVAL_MS = 20_000;
    private static final long MAX_RETRY_MS = 60_000;
    private static final long FAILURE_GRACE_MS = 120_000;
    private static final long SOURCE_AGE_MS = 120_000;
    private static final long WARNING_MS = 300_000;

    private final Consumer<MarketReply> onReply;
    private final Runnable onUnavailable;
    private final Runnable onFailedRequests;
    private final Runnable onFrozenSource;
    private final HypixelAPI api;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Runnable> clientExecutor;
    private final LongSupplier clock;
    private final Runnable abortRequest;

    private volatile boolean running;
    private volatile long generation;
    private volatile long latestSourceTime = -1;
    private volatile long healthRevision;
    private volatile boolean unavailable;
    private volatile long failureEpisode;
    private volatile long failureStartedAt = -1;
    private volatile long frozenEpisode;
    private volatile boolean frozenWarningDelivered;
    // Remaining worker-owned state.
    private ScheduledFuture<?> scheduledFetch;
    private ScheduledFuture<?> sourceExpiry;
    private ScheduledFuture<?> failureGrace;
    private ScheduledFuture<?> failureWarning;
    private ScheduledFuture<?> frozenWarning;
    private CompletableFuture<SkyBlockBazaarReply> inFlight;
    private int failedRequests;
    private long publicationStartedAt = -1;
    private boolean frozenWarningIssued;
    // These fields are only read/written while delivering on the client.
    private long clientRun = -1;
    private long clientSourceTime = -1;
    private boolean clientUnavailable;

    public BazaarPoller(
        Consumer<MarketReply> onReply,
        Runnable onUnavailable,
        Runnable onFailedRequests,
        Runnable onFrozenSource
    ) {
        this(onReply, onUnavailable, onFailedRequests, onFrozenSource, new BoundedBazaarHttpClient());
    }

    private BazaarPoller(
        Consumer<MarketReply> onReply,
        Runnable onUnavailable,
        Runnable onFailedRequests,
        Runnable onFrozenSource,
        BoundedBazaarHttpClient transport
    ) {
        this(onReply, onUnavailable, onFailedRequests, onFrozenSource, new HypixelAPI(transport),
            Executors.newSingleThreadScheduledExecutor(task -> {
                var thread = new Thread(task, "bazaar-poller");
                thread.setDaemon(true);
                return thread;
            }), task -> Minecraft.getInstance().execute(task), System::currentTimeMillis,
            transport::abortCurrentRequest);
    }

    BazaarPoller(
        Consumer<MarketReply> onReply,
        Runnable onUnavailable,
        Runnable onFailedRequests,
        Runnable onFrozenSource,
        HypixelAPI api,
        ScheduledExecutorService scheduler,
        Consumer<Runnable> clientExecutor,
        LongSupplier clock,
        Runnable abortRequest
    ) {
        this.onReply = Objects.requireNonNull(onReply);
        this.onUnavailable = Objects.requireNonNull(onUnavailable);
        this.onFailedRequests = Objects.requireNonNull(onFailedRequests);
        this.onFrozenSource = Objects.requireNonNull(onFrozenSource);
        this.api = Objects.requireNonNull(api);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.clock = Objects.requireNonNull(clock);
        this.abortRequest = Objects.requireNonNull(abortRequest);
    }

    public void start() {
        if (this.running || this.scheduler.isShutdown()) {
            return;
        }
        this.running = true;
        long run = ++this.generation;
        this.execute(() -> {
            if (!this.isCurrent(run)) {
                return;
            }
            this.cancelPendingWork();
            this.latestSourceTime = -1;
            this.publicationStartedAt = -1;
            this.failedRequests = 0;
            this.failureStartedAt = -1;
            this.unavailable = false;
            this.healthRevision++;
            this.failureEpisode++;
            this.frozenEpisode++;
            this.frozenWarningIssued = false;
            this.frozenWarningDelivered = false;
            this.fetch(run);
        });
    }

    public void stop() {
        this.running = false;
        this.generation++;
        this.abortRequest.run();
        this.execute(this::cancelPendingWork);
    }

    private boolean isCurrent(long run) {
        return this.running && this.generation == run;
    }

    private void execute(Runnable task) {
        try {
            this.scheduler.execute(task);
        } catch (RejectedExecutionException error) {
            if (!this.scheduler.isShutdown()) {
                throw error;
            }
        }
    }

    private ScheduledFuture<?> schedule(long run, long delayMs, Runnable task) {
        if (!this.isCurrent(run)) {
            return null;
        }
        try {
            return this.scheduler.schedule(() -> {
                if (this.isCurrent(run)) {
                    task.run();
                }
            }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException error) {
            if (!this.scheduler.isShutdown()) {
                throw error;
            }
            return null;
        }
    }

    private void scheduleFetch(long run, long delayMs) {
        this.scheduledFetch = this.schedule(run, delayMs, () -> {
            this.scheduledFetch = null;
            this.fetch(run);
        });
    }

    private void fetch(long run) {
        if (!this.isCurrent(run)) {
            return;
        }
        Try.of(this.api::getSkyBlockBazaar).onSuccess(request -> {
            if (!this.isCurrent(run)) {
                // Stop may have run before the SDK admitted its physical HTTP request.
                this.abortRequest.run();
                if (request != null) {
                    request.cancel(true);
                }
                return;
            }
            if (request == null) {
                this.failed(run, new IllegalStateException("Bazaar request is null"));
                return;
            }
            this.inFlight = request;
            request.whenComplete((reply, error) -> this.execute(() -> {
                if (!this.isCurrent(run) || this.inFlight != request) {
                    return;
                }
                this.inFlight = null;
                if (error != null) {
                    this.failed(run, error);
                } else {
                    this.process(run, reply);
                }
            }));
        }).onFailure(error -> this.failed(run, error));
    }

    private void process(long run, SkyBlockBazaarReply reply) {
        Try.run(() -> {
            if (reply == null || !reply.isSuccess()) {
                throw new IllegalArgumentException("Bazaar reply is null or unsuccessful");
            }
            long sourceTime = ((SkyBlockBazaarReplyAccessor) reply).getLastUpdated();
            MarketSnapshot snapshot = MarketSnapshot.fromProducts(reply.getProducts());
            if (sourceTime <= 0 || !snapshot.available()) {
                throw new IllegalArgumentException("Bazaar reply has no valid source timestamp/products");
            }
            this.succeeded();
            if (sourceTime > this.latestSourceTime) {
                this.latestSourceTime = sourceTime;
                this.publicationStartedAt = this.clock.getAsLong();
                this.frozenEpisode++;
                this.frozenWarningIssued = false;
                this.frozenWarningDelivered = false;
                cancel(this.frozenWarning);
                this.frozenWarning = null;
                cancel(this.sourceExpiry);
                this.sourceExpiry = this.schedule(run, sourceTime + SOURCE_AGE_MS - this.clock.getAsLong(), () -> {
                    this.sourceExpiry = null;
                    this.markUnavailable(run);
                });
            }
            if (sourceTime == this.latestSourceTime) {
                if (this.usable(sourceTime)) {
                    if (this.unavailable) {
                        this.unavailable = false;
                        this.healthRevision++;
                    }
                    this.deliverCandidate(run, sourceTime, snapshot);
                } else {
                    this.markUnavailable(run);
                }
            }
            this.scheduleFrozenWarning(run);
            this.scheduleFetch(run, NORMAL_INTERVAL_MS + ThreadLocalRandom.current().nextLong(200, 400));
        }).onFailure(error -> this.failed(run, error));
    }

    private boolean usable(long sourceTime) {
        return this.clock.getAsLong() - sourceTime < SOURCE_AGE_MS;
    }

    private void resetClientRun(long run) {
        if (this.clientRun != run) {
            this.clientRun = run;
            this.clientSourceTime = -1;
            this.clientUnavailable = false;
        }
    }

    private void deliverCandidate(long run, long sourceTime, MarketSnapshot snapshot) {
        this.dispatch(() -> {
            if (!this.isCurrent(run) || sourceTime != this.latestSourceTime) {
                return;
            }
            if (!this.usable(sourceTime)) {
                this.deliverUnavailable(run);
                return;
            }
            if (this.unavailable) {
                return;
            }
            this.resetClientRun(run);
            boolean advanced = sourceTime > this.clientSourceTime;
            this.clientSourceTime = sourceTime;
            this.clientUnavailable = false;
            // Even unchanged candidates reach recovery: a previous quiet baseline may have failed.
            this.onReply.accept(new MarketReply(snapshot, advanced));
        });
    }

    private void markUnavailable(long run) {
        if (this.unavailable) {
            return;
        }
        this.unavailable = true;
        long revision = ++this.healthRevision;
        this.dispatch(() -> {
            if (this.isCurrent(run) && this.unavailable && revision == this.healthRevision) {
                this.deliverUnavailable(run);
            }
        });
    }

    private void deliverUnavailable(long run) {
        this.resetClientRun(run);
        if (!this.clientUnavailable) {
            this.clientUnavailable = true;
            this.onUnavailable.run();
        }
    }

    private void succeeded() {
        this.failedRequests = 0;
        this.failureStartedAt = -1;
        this.failureEpisode++;
        cancel(this.failureGrace);
        cancel(this.failureWarning);
        this.failureGrace = null;
        this.failureWarning = null;
    }

    private void failed(long run, Throwable error) {
        if (!this.isCurrent(run)) {
            return;
        }
        if (this.failureStartedAt < 0) {
            this.failureStartedAt = this.clock.getAsLong();
            long episode = ++this.failureEpisode;
            this.frozenEpisode++;
            cancel(this.frozenWarning);
            this.frozenWarning = null;
            this.frozenWarningIssued = this.frozenWarningDelivered;
            this.failureGrace = this.schedule(run, FAILURE_GRACE_MS, () -> {
                this.failureGrace = null;
                if (this.failureStartedAt >= 0 && episode == this.failureEpisode) {
                    this.markUnavailable(run);
                }
            });
            this.failureWarning = this.schedule(run, WARNING_MS, () -> {
                this.failureWarning = null;
                this.dispatch(() -> {
                    if (this.isCurrent(run) && this.failureStartedAt >= 0 && episode == this.failureEpisode) {
                        this.onFailedRequests.run();
                    }
                });
            });
            log.warn("Bazaar polling failed; retrying automatically", error);
        } else {
            log.debug("Bazaar retry failed: {}", error.toString());
        }
        this.failedRequests = Math.min(this.failedRequests + 1, 7);
        long base = Math.min(MAX_RETRY_MS, 1_000L << (this.failedRequests - 1));
        long delay = Math.min(MAX_RETRY_MS, base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 5)));
        var cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BadStatusCodeException status) {
            if (status.getStatusCode() == 429) {
                delay = MAX_RETRY_MS;
            } else if (status.getStatusCode() == 503) {
                delay = Math.max(delay, 5_000);
            }
        }
        this.scheduleFetch(run, delay);
    }

    private void scheduleFrozenWarning(long run) {
        if (this.frozenWarningIssued || this.frozenWarning != null || this.publicationStartedAt < 0) {
            return;
        }
        long episode = this.frozenEpisode;
        this.frozenWarning = this.schedule(run, this.publicationStartedAt + WARNING_MS - this.clock.getAsLong(), () -> {
            this.frozenWarning = null;
            if (episode != this.frozenEpisode || this.failureStartedAt >= 0) {
                return;
            }
            this.frozenWarningIssued = true;
            this.dispatch(() -> {
                if (this.isCurrent(run) && episode == this.frozenEpisode && this.failureStartedAt < 0) {
                    this.frozenWarningDelivered = true;
                    this.onFrozenSource.run();
                }
            });
        });
    }

    private void dispatch(Runnable task) {
        Try.run(() -> this.clientExecutor.accept(task))
            .onFailure(error -> log.error("Could not dispatch Bazaar observation", error));
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void cancelPendingWork() {
        cancel(this.scheduledFetch);
        cancel(this.sourceExpiry);
        cancel(this.failureGrace);
        cancel(this.failureWarning);
        cancel(this.frozenWarning);
        this.scheduledFetch = null;
        this.sourceExpiry = null;
        this.failureGrace = null;
        this.failureWarning = null;
        this.frozenWarning = null;
        if (this.inFlight != null) {
            this.abortRequest.run();
            this.inFlight.cancel(true);
            this.inFlight = null;
        }
    }

    @Override
    public void close() {
        this.running = false;
        this.generation++;
        this.abortRequest.run();
        this.execute(() -> {
            this.cancelPendingWork();
            try {
                this.api.shutdown();
            } finally {
                this.scheduler.shutdownNow();
            }
        });
        this.scheduler.shutdown();
    }

    public record MarketReply(MarketSnapshot snapshot, boolean advanced) {}
}
