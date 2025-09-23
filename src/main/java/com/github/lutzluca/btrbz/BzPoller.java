package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import io.vavr.control.Try;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

/**
 * possible concurrency issues: this class uses a single-threaded ScheduledExecutorService for all
 * operations, so all scheduled tasks and async callbacks (via whenCompleteAsync with explicit
 * executor) execute on the same scheduler thread, ensuring sequential access to instance variables
 * and eliminating concurrency issues I think (눈_눈)
 */
public class BzPoller {

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

    private static final HypixelAPI API = new HypixelAPI(new ApacheHttpClient(getApiKey()));

    private final Consumer<Map<String, Product>> onReply;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread thread = new Thread(r, "BazaarPoller-Thread");
            thread.setDaemon(true);
            return thread;
        });

    private long lastKnownUpdateTime = -1;
    private int unchangedDataRetries = 0;

    public BzPoller(@NotNull Consumer<Map<String, Product>> onReply) {
        this.onReply = Objects.requireNonNull(onReply);
        this.scheduleFetch(0, "Initial fetch");
    }

    private static UUID getApiKey() {
        return Optional
            .ofNullable(System.getenv("HYPIXEL_API_KEY"))
            .map(UUID::fromString)
            .orElseGet(UUID::randomUUID);
    }

    private void scheduleFetch(long delayMs, String reason) {
        this.scheduler.schedule(this::fetchBazaarData, delayMs, TimeUnit.MILLISECONDS);
    }

    private void fetchBazaarData() {
        // @formatter:off
        API.getSkyBlockBazaar()
           .whenCompleteAsync(
               (reply, throwable) -> {
                   if (throwable != null) {
                       this.handleFetchError(throwable);
                       return;
                   }

                   if (reply == null) {
                       this.handleFetchError(new NullPointerException("Bazaar reply is null"));
                       return;
                   }

                   if (!reply.isSuccess()) {
                       this.handleFetchError(new IllegalStateException("Bazaar reply unsuccessful"));
                       return;
                   }

                   this.processBazaarReply(reply);
               },
               this.scheduler
           );
        // @formatter:on
    }

    private void processBazaarReply(SkyBlockBazaarReply reply) {
        Try.of(() -> (SkyBlockBazaarReplyAccessor) reply).onSuccess((accessor) -> {
            long currentUpdateTime = accessor.getLastUpdated();
            boolean changed = currentUpdateTime != this.lastKnownUpdateTime;

            if (changed) {
                this.handleChangedData(currentUpdateTime, reply.getProducts());
            } else {
                this.handleUnchangedData();
            }

            this.lastKnownUpdateTime = currentUpdateTime;
            Notifier.logDebug("Bazaar data fetched successfully - Data {}, Last Updated: {}",
                changed ? "changed" : "unchanged", Utils.formatUtcTimestampMillis(currentUpdateTime)
            );
        }).onFailure(err -> {
            Notifier.logError("Reply does not implement expected accessor. {}", err);
            this.scheduleFetch(ERROR_BACKOFF_MS,
                "Error recovery - SkyBlockBazaarReplyAccessor cast failed"
            );
        });
    }

    private void handleChangedData(long currentUpdateTime, Map<String, Product> products) {
        this.unchangedDataRetries = 0;

        if (this.lastKnownUpdateTime != -1) {
            long diffMs = currentUpdateTime - this.lastKnownUpdateTime;
            Notifier.logDebug("Bazaar data updated after {}s", diffMs / 1000.0);
        }

        Optional
            .ofNullable(MinecraftClient.getInstance())
            .ifPresent(client -> client.execute(() -> onReply.accept(products)));

        long jitter = ThreadLocalRandom.current().nextLong(200, 400);
        this.scheduleFetch(BAZAAR_UPDATE_TIME_MS + jitter, "Regular interval fetch");
    }

    private void handleUnchangedData() {
        this.unchangedDataRetries++;

        if (this.unchangedDataRetries <= MAX_UNCHANGED_RETRIES) {
            Notifier.logInfo("Data unchanged (attempt {}/{}), retrying in {}ms",
                this.unchangedDataRetries, MAX_UNCHANGED_RETRIES, UNCHANGED_DATA_BACKOFF_MS
            );

            this.scheduleFetch(UNCHANGED_DATA_BACKOFF_MS,
                String.format("Unchanged data retry #%d", this.unchangedDataRetries)
            );
        } else {
            Notifier.logWarn("Bazaar data has been unchanged for {} consecutive attempts. "
                    + "Reverting to normal polling interval. This may indicate an API issue.",
                MAX_UNCHANGED_RETRIES
            );

            this.unchangedDataRetries = 0;
            long jitter = ThreadLocalRandom.current().nextLong(200, 400);
            this.scheduleFetch(BAZAAR_UPDATE_TIME_MS + jitter, "Post-unchanged-limit normal fetch");
        }
    }

    private void handleFetchError(Throwable throwable) {
        Notifier.logWarn("Error occurred while fetching bazaar data. Retrying in {}ms. {}",
            ERROR_BACKOFF_MS, throwable.getMessage()
        );
        this.scheduleFetch(ERROR_BACKOFF_MS, "Error recovery: API fetch error");
    }
}
