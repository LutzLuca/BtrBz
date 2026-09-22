package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import io.vavr.control.Try;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
import net.hypixel.api.exceptions.BadStatusCodeException;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

/**
 * Polling runs on one worker. Start/stop run on the client thread and invalidate delivery immediately;
 * only publishing a current result is dispatched back to the client.
 */
@Slf4j
public class BazaarPoller implements AutoCloseable {
    private static final long BAZAAR_UPDATE_TIME_MS = 20_000;
    private static final long REQUEST_TIMEOUT_MS = 30_000;
    private static final long MAX_ERROR_BACKOFF_MS = 60_000;
    private static final long OUTAGE_WARNING_MS = 5 * 60_000;

    private final Consumer<Map<String, Product>> onReply;
    private final Runnable onLongOutage;
    private final HypixelAPI api;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Runnable> clientExecutor;

    private volatile boolean running;
    private volatile long generation;
    // Worker-owned state below.
    private ScheduledFuture<?> scheduledFetch;
    private ScheduledFuture<?> requestTimeout;
    private ScheduledFuture<?> outageWarning;
    private CompletableFuture<SkyBlockBazaarReply> inFlight;

    private long lastKnownUpdateTime = -1;
    private int failedRequests;
    private volatile boolean outageActive;

    public BazaarPoller(@NotNull Consumer<Map<String, Product>> onReply, Runnable onLongOutage) {
        this(
            onReply,
            onLongOutage,
            // The SDK requires a key for its transport, but getSkyBlockBazaar is keyless.
            new HypixelAPI(new ApacheHttpClient(new UUID(0, 0))),
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "bazaar-poller");
                thread.setDaemon(true);
                return thread;
            }),
            task -> Minecraft.getInstance().execute(task));
    }

    BazaarPoller(
        Consumer<Map<String, Product>> onReply,
        Runnable onLongOutage,
        HypixelAPI api,
        ScheduledExecutorService scheduler,
        Consumer<Runnable> clientExecutor
    ) {
        this.onReply = Objects.requireNonNull(onReply);
        this.onLongOutage = Objects.requireNonNull(onLongOutage);
        this.api = Objects.requireNonNull(api);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
    }

    public void start() {
        if (this.running || this.scheduler.isShutdown()) {
            return;
        }
        this.running = true;
        long run = ++this.generation;
        log.debug("Started Bazaar polling generation {}", run);
        this.execute(() -> {
            if (this.isCurrent(run)) {
                this.cancelPendingFetch();
                this.cancelOutageWarning();
                this.lastKnownUpdateTime = -1;
                this.failedRequests = 0;
                this.outageActive = false;
                this.fetchBazaarData(run);
            }
        });
    }

    public void stop() {
        if (this.running) {
            log.debug("Stopping Bazaar polling generation {}", this.generation);
        }
        this.running = false;
        this.generation++;
        this.outageActive = false;
        this.execute(() -> {
            this.cancelPendingFetch();
            this.cancelOutageWarning();
        });
    }

    private void cancelPendingFetch() {
        if (this.scheduledFetch != null) {
            this.scheduledFetch.cancel(false);
            this.scheduledFetch = null;
        }
        if (this.requestTimeout != null) {
            this.requestTimeout.cancel(false);
            this.requestTimeout = null;
        }
        if (this.inFlight != null) {
            this.inFlight.cancel(true);
            this.inFlight = null;
        }
    }

    private void cancelOutageWarning() {
        if (this.outageWarning != null) {
            this.outageWarning.cancel(false);
            this.outageWarning = null;
        }
    }

    private boolean isCurrent(long run) {
        return this.running && run == this.generation;
    }

    private void scheduleFetch(long run, long delayMs, String reason) {
        if (!this.isCurrent(run)) {
            return;
        }
        try {
            this.scheduledFetch = this.scheduler.schedule(() -> {
                if (this.isCurrent(run)) {
                    this.scheduledFetch = null;
                    this.fetchBazaarData(run);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
            log.trace("Scheduled new fetch: {}", reason);
        } catch (RejectedExecutionException error) {
            if (!this.scheduler.isShutdown()) {
                throw error;
            }
        }
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

    private void fetchBazaarData(long run) {
        Try.of(this.api::getSkyBlockBazaar).onSuccess(request -> {
            if (request == null) {
                this.handleFetchError(run, new IllegalStateException("Bazaar request is null"));
                return;
            }
            this.inFlight = request;
            this.requestTimeout = this.scheduler.schedule(() -> {
                if (!this.isCurrent(run) || this.inFlight != request) {
                    return;
                }
                this.inFlight = null;
                this.requestTimeout = null;
                request.cancel(true);
                this.handleFetchError(run, new TimeoutException("Bazaar request exceeded 30 seconds"));
            }, REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            request.whenComplete((reply, throwable) -> this.execute(() -> {
                if (!this.isCurrent(run) || this.inFlight != request) {
                    return;
                }
                this.inFlight = null;
                this.requestTimeout.cancel(false);
                this.requestTimeout = null;
                if (throwable != null) {
                    this.handleFetchError(run, throwable);
                    return;
                }

                if (reply == null) {
                    this.handleFetchError(run, new NullPointerException("Bazaar reply is null"));
                    return;
                }

                if (!reply.isSuccess()) {
                    this.handleFetchError(run, new IllegalStateException("Bazaar reply unsuccessful"));
                    return;
                }

                this.processBazaarReply(run, reply);
            }));
        }).onFailure(error -> this.handleFetchError(run, error));
    }

    private void processBazaarReply(long run, SkyBlockBazaarReply reply) {
        Try.of(() -> ((SkyBlockBazaarReplyAccessor) reply).getLastUpdated()).onSuccess(currentUpdateTime -> {
            if (currentUpdateTime <= 0 || reply.getProducts() == null) {
                this.handleFetchError(run, new IllegalArgumentException("Invalid Bazaar reply"));
                return;
            }
            this.recovered();
            boolean changed = currentUpdateTime > this.lastKnownUpdateTime;
            if (changed) {
                this.lastKnownUpdateTime = currentUpdateTime;
                Try.run(() -> this.clientExecutor.accept(() -> {
                    if (this.isCurrent(run)) {
                        this.onReply.accept(reply.getProducts());
                    }
                })).onFailure(error -> log.error("Could not dispatch Bazaar update", error));
            }
            this.scheduleNormalFetch(run);

            log.trace(
                "Bazaar data fetched successfully - Data {}, Last Updated: {}",
                changed ? "changed" : "unchanged",
                currentUpdateTime);
        }).onFailure(err -> this.handleFetchError(run, err));
    }

    private void scheduleNormalFetch(long run) {
        long jitter = ThreadLocalRandom.current().nextLong(200, 400);
        this.scheduleFetch(run, BAZAAR_UPDATE_TIME_MS + jitter, "Regular interval fetch");
    }

    private void recovered() {
        if (this.failedRequests > 0) {
            log.info("Bazaar polling recovered after {} failed requests", this.failedRequests);
        }
        this.failedRequests = 0;
        this.outageActive = false;
        this.cancelOutageWarning();
    }

    private void handleFetchError(long run, Throwable throwable) {
        if (!this.isCurrent(run)) {
            return;
        }
        if (!this.outageActive) {
            this.outageActive = true;
            this.outageWarning = this.scheduler.schedule(() -> {
                this.outageWarning = null;
                if (this.isCurrent(run) && this.outageActive) {
                    this.clientExecutor.accept(() -> {
                        if (this.isCurrent(run) && this.outageActive) {
                            this.onLongOutage.run();
                        }
                    });
                }
            }, OUTAGE_WARNING_MS, TimeUnit.MILLISECONDS);
            log.warn("Bazaar polling failed; retrying automatically", throwable);
        } else {
            log.debug("Bazaar polling retry failed: {}", throwable.toString());
        }
        if (this.failedRequests < Integer.MAX_VALUE) {
            this.failedRequests++;
        }
        long base = Math.min(MAX_ERROR_BACKOFF_MS, 1_000L << Math.min(this.failedRequests - 1, 6));
        long delay = Math.min(MAX_ERROR_BACKOFF_MS,
            base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 5)));
        var cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BadStatusCodeException status) {
            // The SDK does not expose Retry-After; use a conservative delay for throttling.
            if (status.getStatusCode() == 429) {
                delay = MAX_ERROR_BACKOFF_MS;
            } else if (status.getStatusCode() == 503) {
                delay = Math.max(delay, 5_000);
            }
        }
        this.scheduleFetch(run, delay, "Error recovery");
    }

    @Override
    public void close() {
        this.running = false;
        this.generation++;
        this.outageActive = false;
        this.execute(() -> {
            this.cancelPendingFetch();
            this.cancelOutageWarning();
            try {
                this.api.shutdown();
            } finally {
                // Discard cancelled timers too; graceful shutdown can retain them until their deadline.
                this.scheduler.shutdownNow();
            }
        });
        this.scheduler.shutdown();
    }
}
