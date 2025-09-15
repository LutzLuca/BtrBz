package com.github.lutzluca.btrbz;

import static com.github.lutzluca.btrbz.BtrBz.LOGGER;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import com.google.gson.Gson;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;

/**
 * possible concurrency issues: this class uses a single-threaded ScheduledExecutorService for all operations, so all
 * scheduled tasks and async callbacks (via whenCompleteAsync with explicit executor) execute on the same scheduler
 * thread, ensuring sequential access to instance variables and eliminating concurrency issues I think (눈_눈)
 */
public class BzPoller {

    /*
     * maybe on unchanged data use exponential backoff, starting at 100ms, doubling each time up a
     * max as unchanged data should indicate that the bz has not been updated and should update
     * soon (this should be the case most of the time, else the API is unable to respond
     * with updated data). But this is good enough for "now".
     */

    private static final long BAZAAR_UPDATE_TIME_MS = 20_000;
    private static final long UNCHANGED_DATA_BACKOFF_MS = 250;
    private static final long ERROR_BACKOFF_MS = 500;
    private static final int MAX_UNCHANGED_RETRIES = 5;

    private static final HypixelAPI API = new HypixelAPI(new ApacheHttpClient(getApiKey()));

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread thread = new Thread(r, "BazaarPoller-Thread");
            thread.setDaemon(true);
            return thread;
        });

    private long lastKnownUpdateTime = -1;
    private int unchangedDataRetries = 0;

    public BzPoller() {
        scheduleFetch(0, "Initial fetch");
    }

    private static UUID getApiKey() {
        return Optional.ofNullable(System.getenv("HYPIXEL_API_KEY"))
                       .map(UUID::fromString)
                       .orElseGet(UUID::randomUUID);
    }

    private void scheduleFetch(long delayMs, String reason) {
        scheduler.schedule(() -> {
                LOGGER.info("Executing bazaar fetch: {}", reason);
                fetchBazaarData();
            }, delayMs, TimeUnit.MILLISECONDS
        );
    }

    private void fetchBazaarData() {
        // @formatter:off
        API.getSkyBlockBazaar()
           .whenCompleteAsync(
               (reply, throwable) -> {
                   if (throwable != null) {
                       handleFetchError(throwable);
                       return;
                   }

                   if (reply == null) {
                       handleFetchError(new NullPointerException("Bazaar reply is null"));
                       return;
                   }

                   if (!reply.isSuccess()) {
                       handleFetchError(new IllegalStateException("Bazaar reply unsuccessful"));
                       return;
                   }

                   processBazaarReply(reply);
               },
               scheduler
           );
        // @formatter:on
    }

    private void processBazaarReply(SkyBlockBazaarReply reply) {
        Try.of(() -> (SkyBlockBazaarReplyAccessor) reply).onSuccess((accessor) -> {
            long currentUpdateTime = accessor.getLastUpdated();
            boolean changed = currentUpdateTime != lastKnownUpdateTime;

            if (changed) {
                handleChangedData(currentUpdateTime, reply.getProducts());
            } else {
                handleUnchangedData();
            }

            lastKnownUpdateTime = currentUpdateTime;
            LOGGER.info("Bazaar data fetched successfully - Data {}, Last Updated: {}",
                changed ? "changed" : "unchanged", Utils.formatUtcTimestampMillis(currentUpdateTime)
            );
        }).onFailure(err -> {
            LOGGER.error("Reply does not implement expected accessor", err);
            scheduleFetch(ERROR_BACKOFF_MS, "Error recovery - SkyBlockBazaarReplyAccessor cast failed");
        });
    }

    private void handleChangedData(long currentUpdateTime, Map<String, Product> products) {
        unchangedDataRetries = 0;

        if (lastKnownUpdateTime != -1) {
            long diffMs = currentUpdateTime - lastKnownUpdateTime;
            LOGGER.info("Bazaar data updated after {} seconds", diffMs / 1000.0);
        }

        // TODO: do something meaningful here
        Try.of(() -> {
               var gson = new Gson();
               return gson.toJson(products);
           })
           .onSuccess(json ->
               Utils.atomicDumpToFile(
                        Path.of(System.getProperty("user.dir")).resolve("data.json").toString(),
                        json
                    )
                    .onSuccess(path -> LOGGER.info("Dumped bazaar data to {}", path))
                    .onFailure(err -> LOGGER.error("Failed to write bazaar data to file", err))
           )
           .onFailure(err -> LOGGER.error("Failed to process product data", err));

        long jitter = ThreadLocalRandom.current().nextLong(200, 400);
        scheduleFetch(BAZAAR_UPDATE_TIME_MS + jitter, "Regular interval fetch");
    }

    private void handleUnchangedData() {
        unchangedDataRetries++;

        if (unchangedDataRetries <= MAX_UNCHANGED_RETRIES) {
            LOGGER.info("Data unchanged (attempt {}/{}), retrying in {}ms", unchangedDataRetries,
                MAX_UNCHANGED_RETRIES, UNCHANGED_DATA_BACKOFF_MS
            );

            scheduleFetch(UNCHANGED_DATA_BACKOFF_MS,
                String.format("Unchanged data retry #%d", unchangedDataRetries)
            );
        } else {
            LOGGER.warn("Bazaar data has been unchanged for {} consecutive attempts. "
                    + "Reverting to normal polling interval. This may indicate an API issue.",
                MAX_UNCHANGED_RETRIES
            );

            unchangedDataRetries = 0;
            long jitter = ThreadLocalRandom.current().nextLong(200, 400);
            scheduleFetch(BAZAAR_UPDATE_TIME_MS + jitter, "Post-unchanged-limit normal fetch");
        }
    }

    private void handleFetchError(Throwable throwable) {
        LOGGER.warn("Error occurred while fetching bazaar data. Retrying in {}ms", ERROR_BACKOFF_MS, throwable);
        scheduleFetch(ERROR_BACKOFF_MS, "Error recovery: API fetch error");
    }
}
