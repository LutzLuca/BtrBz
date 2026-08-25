package com.github.lutzluca.btrbz.data;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarPollerTest {

    @Test
    void closeShutsDownSchedulerAndHttpClient() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var httpClient = new RecordingHttpClient();
        var poller = new BazaarPoller(products -> {}, new HypixelAPI(httpClient), scheduler);

        poller.close();

        Assertions.assertTrue(scheduler.isShutdown());
        Assertions.assertTrue(httpClient.shutdown);
    }

    @Test
    void constructionIgnoresSchedulingRejectedDuringShutdown() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.shutdownNow();
        var httpClient = new RecordingHttpClient();

        var poller = Assertions.assertDoesNotThrow(
            () -> new BazaarPoller(products -> {}, new HypixelAPI(httpClient), scheduler));
        poller.close();
    }

    private static final class RecordingHttpClient implements HypixelHttpClient {

        private boolean shutdown;

        @Override
        public CompletableFuture<HypixelHttpResponse> makeRequest(String url) {
            return new CompletableFuture<>();
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
