package com.github.lutzluca.btrbz.data;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoundedBazaarHttpClientTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stalledRequestEndsAndLeavesCapacityForRecovery(boolean abortExplicitly) throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3_000);
            var client = new BoundedBazaarHttpClient(1_000, 6_000, abortExplicitly ? 5_000 : 500);
            try {
                String url = "http://127.0.0.1:" + server.getLocalPort() + "/bazaar";
                var stalled = client.makeRequest(url);
                try (var _ = server.accept()) {
                    Assertions.assertThrows(RejectedExecutionException.class, () -> client.makeRequest(url));
                    if (abortExplicitly) {
                        client.abortCurrentRequest();
                    }
                    Assertions.assertThrows(ExecutionException.class, () -> stalled.get(3, TimeUnit.SECONDS));

                    var recovered = requestWhenWorkerIsFree(client, url);
                    try (var connection = server.accept()) {
                        connection.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: 2\r\nConnection: close\r\n\r\nok")
                                .getBytes(StandardCharsets.US_ASCII));
                        connection.getOutputStream().flush();
                        var response = recovered.get(3, TimeUnit.SECONDS);
                        Assertions.assertEquals(200, response.getStatusCode());
                        Assertions.assertEquals("ok", response.getBody());
                    }
                }
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    void stoppingPollerAbortsComposedSdkRequestBeforeRestart() throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3_000);
            var client = new BoundedBazaarHttpClient(1_000, 6_000, 5_000);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/bazaar";
            var api = new HypixelAPI(client) {
                @Override
                public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                    // Like the SDK, this returns a stage composed from the transport future.
                    return client.makeRequest(url).thenApply(_ -> null);
                }
            };
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var poller = new BazaarPoller(_ -> {}, () -> {}, () -> {}, api, scheduler, Runnable::run,
                () -> 0L, client::abortCurrentRequest);
            try {
                poller.start();
                try (var _ = server.accept()) {
                    poller.stop();
                    poller.start();
                    try (var connection = server.accept()) {
                        connection.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: 2\r\nConnection: close\r\n\r\nok")
                                .getBytes(StandardCharsets.US_ASCII));
                        connection.getOutputStream().flush();
                    }
                }
            } finally {
                poller.close();
                Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    private static CompletableFuture<HypixelHttpResponse> requestWhenWorkerIsFree(
        BoundedBazaarHttpClient client,
        String url
    ) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                return client.makeRequest(url);
            } catch (RejectedExecutionException _) {
                Thread.sleep(10);
            }
        }
        throw new AssertionError("Timed-out HTTP request still occupies the only worker");
    }
}
