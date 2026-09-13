package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import io.vavr.control.Try;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
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

    /*
     * maybe on unchanged data use exponential backoff, starting at 100ms, doubling each time up a
     * max as unchanged data should indicate that the bz has not been updated and should update soon
     * (this should be the case most of the time, else the API is unable to respond with updated
     * data). But this is good enough for "now".
     */

    private static final long BAZAAR_UPDATE_TIME_MS = 20_000;
    private static final long UNCHANGED_DATA_BACKOFF_MS = 250;
    private static final long ERROR_BACKOFF_MS = 500;
    private static final int MAX_UNCHANGED_RETRIES = 5;
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Consumer<Map<String, Product>> onReply;
    private final HypixelAPI api;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Runnable> clientExecutor;

    private volatile boolean running;
    private volatile long generation;
    // Worker-owned state below.
    private ScheduledFuture<?> scheduledFetch;
    private CompletableFuture<SkyBlockBazaarReply> inFlight;

    private long lastKnownUpdateTime = -1;
    private int unchangedDataRetries = 0;

    public BazaarPoller(@NotNull Consumer<Map<String, Product>> onReply) {
        this(
            onReply,
            new HypixelAPI(new ApacheHttpClient(getApiKey())),
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "bazaar-poller");
                thread.setDaemon(true);
                return thread;
            }),
            task -> Minecraft.getInstance().execute(task));
    }

    BazaarPoller(
        Consumer<Map<String, Product>> onReply,
        HypixelAPI api,
        ScheduledExecutorService scheduler,
        Consumer<Runnable> clientExecutor
    ) {
        this.onReply = Objects.requireNonNull(onReply);
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
                this.lastKnownUpdateTime = -1;
                this.unchangedDataRetries = 0;
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
        this.execute(this::cancelPendingFetch);
    }

    private void cancelPendingFetch() {
        if (this.scheduledFetch != null) {
            this.scheduledFetch.cancel(false);
            this.scheduledFetch = null;
        }
        if (this.inFlight != null) {
            this.inFlight.cancel(true);
            this.inFlight = null;
        }
    }

    private static UUID getApiKey() {
        return Optional
            .ofNullable(System.getenv("HYPIXEL_API_KEY"))
            .map(UUID::fromString)
            .orElseGet(UUID::randomUUID);
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
            this.inFlight = request;
            request.whenCompleteAsync((reply, throwable) -> {
                if (!this.isCurrent(run)) {
                    return;
                }
                this.inFlight = null;
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
            }, this.scheduler);
        }).onFailure(error -> this.handleFetchError(run, error));
    }

    private void processBazaarReply(long run, SkyBlockBazaarReply reply) {
        Try.of(() -> (SkyBlockBazaarReplyAccessor) reply).onSuccess(accessor -> {
            long currentUpdateTime = accessor.getLastUpdated();
            boolean changed = currentUpdateTime != this.lastKnownUpdateTime;
            this.lastKnownUpdateTime = currentUpdateTime;

            if (changed) {
                this.handleChangedData(run, reply.getProducts());
            } else {
                this.handleUnchangedData(run);
            }

            log.trace(
                "Bazaar data fetched successfully - Data {}, Last Updated: {}",
                changed ? "changed" : "unchanged",
                formatTimestamp(currentUpdateTime));
        }).onFailure(err -> {
            log.warn("Reply does not implement expected accessor.", err);
            this.scheduleFetch(
                run,
                ERROR_BACKOFF_MS,
                "Error recovery - SkyBlockBazaarReplyAccessor cast failed");
        });
    }

    private void handleChangedData(long run, Map<String, Product> products) {
        this.unchangedDataRetries = 0;
        this.clientExecutor.accept(() -> {
            if (this.isCurrent(run)) {
                this.onReply.accept(products);
            }
        });

        long jitter = ThreadLocalRandom.current().nextLong(200, 400);
        this.scheduleFetch(run, BAZAAR_UPDATE_TIME_MS + jitter, "Regular interval fetch");
    }

    private static String formatTimestamp(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()).format(LOG_TIMESTAMP);
    }

    private void handleUnchangedData(long run) {
        this.unchangedDataRetries++;

        if (this.unchangedDataRetries <= MAX_UNCHANGED_RETRIES) {
            log.debug(
                "Data unchanged (attempt {}/{}), retrying in {}ms",
                this.unchangedDataRetries,
                MAX_UNCHANGED_RETRIES,
                UNCHANGED_DATA_BACKOFF_MS);

            this.scheduleFetch(
                run,
                UNCHANGED_DATA_BACKOFF_MS,
                String.format("Unchanged data retry #%d", this.unchangedDataRetries));
        } else {
            log.warn(
                "Bazaar data has been unchanged for {} consecutive attempts. Reverting to normal polling interval. "
                    + "This may indicate an API issue.",
                MAX_UNCHANGED_RETRIES);

            this.unchangedDataRetries = 0;
            long jitter = ThreadLocalRandom.current().nextLong(200, 400);
            this.scheduleFetch(run, BAZAAR_UPDATE_TIME_MS + jitter, "Post-unchanged-limit normal fetch");
        }
    }

    private void handleFetchError(long run, Throwable throwable) {
        log.warn(
            "Error occurred while fetching bazaar data. Retrying in {}ms. {}",
            ERROR_BACKOFF_MS,
            throwable.getMessage());
        this.scheduleFetch(run, ERROR_BACKOFF_MS, "Error recovery: API fetch error");
    }

    @Override
    public void close() {
        this.running = false;
        this.generation++;
        this.execute(() -> {
            this.cancelPendingFetch();
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
