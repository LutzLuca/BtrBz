package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.mixin.SkyBlockBazaarReplyAccessor;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.apache.http.conn.DnsResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BoundedBazaarHttpClientTest {
    @Test
    void totalDeadlineCompletesDuringBlockedDnsWithoutReleasingPhysicalCapacity() throws Exception {
        try (var server = server()) {
            var dns = new BlockedDns();
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 200, dns);
            var responder = Executors.newSingleThreadExecutor();
            try {
                var raw = transport.makeRequest("http://bazaar.test:" + server.getLocalPort() + "/expired");
                Assertions.assertTrue(dns.entered.await(2, TimeUnit.SECONDS));
                var failure = Assertions.assertThrows(ExecutionException.class, () -> raw.get(2, TimeUnit.SECONDS));
                Assertions.assertInstanceOf(TimeoutException.class, failure.getCause());
                Assertions.assertThrows(RejectedExecutionException.class, () -> transport.makeRequest(url(server)));

                var received = responder.submit(() -> {
                    for (int attempt = 0; attempt < 2; attempt++) {
                        try (var socket = server.accept()) {
                            String headers = readHeaders(socket);
                            if (!headers.isEmpty()) {
                                respond(socket);
                                return headers;
                            }
                        }
                    }
                    throw new AssertionError("Recovery request never reached the server");
                });
                dns.release.countDown();
                var recovery = requestWhenFree(transport, url(server).replace("/bazaar", "/recovered"));
                Assertions.assertEquals(200, recovery.get(2, TimeUnit.SECONDS).getStatusCode());
                Assertions.assertTrue(received.get(2, TimeUnit.SECONDS).startsWith("GET /recovered "),
                    "The expired request must not send HTTP after DNS returns");
            } finally {
                dns.release.countDown();
                transport.shutdown();
                server.close();
                responder.shutdownNow();
                Assertions.assertTrue(responder.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void stopBeforeSdkAdmissionAbortsTheLaterPhysicalRequestAndAllowsRestart() throws Exception {
        try (var server = server()) {
            var beforeAdmission = new CountDownLatch(1);
            var admit = new CountDownLatch(1);
            var returnStage = new CountDownLatch(1);
            var first = new AtomicBoolean(true);
            var staleStage = new CompletableFuture<CompletableFuture<SkyBlockBazaarReply>>();
            var transport = new BoundedBazaarHttpClient(1_000, 10_000, 10_000);
            var api = new HypixelAPI(transport) {
                @Override
                public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                    if (first.compareAndSet(true, false)) {
                        beforeAdmission.countDown();
                        await(admit);
                        CompletableFuture<SkyBlockBazaarReply> stage = transport.makeRequest(url(server))
                            .thenApply(response -> new Gson().fromJson(response.getBody(), Reply.class));
                        staleStage.complete(stage);
                        await(returnStage);
                        return stage;
                    }
                    return transport.makeRequest(url(server))
                        .thenApply(response -> new Gson().fromJson(response.getBody(), Reply.class));
                }
            };
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var recovered = new CompletableFuture<BazaarPoller.MarketReply>();
            var poller = new BazaarPoller(recovered::complete, () -> {}, () -> {}, () -> {}, api,
                scheduler, Runnable::run, System::currentTimeMillis, transport::abortCurrentRequest);
            try {
                poller.start();
                Assertions.assertTrue(beforeAdmission.await(2, TimeUnit.SECONDS));
                poller.stop();
                poller.start();
                admit.countDown();
                try (var staleSocket = server.accept()) {
                    readRequest(staleSocket);
                    returnStage.countDown();
                    assertClosed(staleSocket);
                    Assertions.assertNotNull(staleStage.get(2, TimeUnit.SECONDS)
                        .handle((_, error) -> error).get(2, TimeUnit.SECONDS));
                    try (var currentSocket = server.accept()) {
                        readRequest(currentSocket);
                        respond(currentSocket);
                        Assertions.assertTrue(recovered.get(2, TimeUnit.SECONDS).snapshot().available());
                    }
                }
            } finally {
                admit.countDown();
                returnStage.countDown();
                poller.close();
                transport.shutdown();
                Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void slowBodyTrickleStillHitsTheTotalDeadline() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 500);
            var writer = Executors.newSingleThreadScheduledExecutor();
            try {
                var result = transport.makeRequest(url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    Assertions.assertThrows(RejectedExecutionException.class,
                        () -> transport.makeRequest(url(server)));
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    var bodyStarted = new CountDownLatch(1);
                    var trickle = writer.scheduleAtFixedRate(() -> {
                        try {
                            socket.getOutputStream().write('x');
                            socket.getOutputStream().flush();
                            bodyStarted.countDown();
                        } catch (IOException _) {
                            // A closed client socket is expected after the total deadline.
                        }
                    }, 0, 30, TimeUnit.MILLISECONDS);
                    Assertions.assertTrue(bodyStarted.await(2, TimeUnit.SECONDS));
                    var failure = Assertions.assertThrows(ExecutionException.class,
                        () -> result.get(2, TimeUnit.SECONDS));
                    Assertions.assertInstanceOf(TimeoutException.class, failure.getCause());
                    assertClosed(socket);
                    trickle.cancel(true);
                }
                var recovered = requestWhenFree(transport, url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    respond(socket);
                    Assertions.assertEquals(200, recovered.get(2, TimeUnit.SECONDS).getStatusCode());
                }
            } finally {
                transport.shutdown();
                writer.shutdownNow();
                Assertions.assertTrue(writer.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void cancellingSdkComposedFutureKeepsPhysicalCapacityUntilExplicitAbort() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 3_000);
            var api = localApi(transport, url(server));
            try {
                var composed = api.getSkyBlockBazaar();
                try (var socket = server.accept()) {
                    readRequest(socket);
                    composed.cancel(true);
                    Assertions.assertThrows(RejectedExecutionException.class, api::getSkyBlockBazaar);
                    socket.setSoTimeout(150);
                    Assertions.assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
                    transport.abortCurrentRequest();
                    assertClosed(socket);
                }
                var recovered = requestWhenFree(transport, url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    respond(socket);
                    Assertions.assertEquals(200, recovered.get(3, TimeUnit.SECONDS).getStatusCode());
                }
            } finally {
                api.shutdown();
            }
        }
    }

    @Test
    void pollerCloseAbortsItsPhysicalRequestAndClosesAdmission() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 4_000, 4_000);
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var poller = new BazaarPoller(_ -> {}, () -> {}, () -> {}, () -> {},
                localApi(transport, url(server)), scheduler, Runnable::run, System::currentTimeMillis,
                transport::abortCurrentRequest);
            try {
                poller.start();
                try (var socket = server.accept()) {
                    readRequest(socket);
                    poller.close();
                    assertClosed(socket);
                    Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
                    Assertions.assertThrows(RejectedExecutionException.class,
                        () -> transport.makeRequest(url(server)));
                }
            } finally {
                poller.close();
                transport.shutdown();
                Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    private static HypixelAPI localApi(BoundedBazaarHttpClient transport, String url) {
        return new HypixelAPI(transport) {
            @Override
            public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                return transport.makeRequest(url)
                    .thenApply(response -> new Gson().fromJson(response.getBody(), Reply.class));
            }
        };
    }

    private static ServerSocket server() throws IOException {
        var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(3_000);
        return server;
    }

    private static String url(ServerSocket server) {
        return "http://127.0.0.1:" + server.getLocalPort() + "/bazaar";
    }

    private static void readRequest(Socket socket) throws IOException {
        Assertions.assertTrue(readHeaders(socket).endsWith("\r\n\r\n"), "Request ended before HTTP headers");
    }

    private static void respond(Socket socket) throws IOException {
        String body = """
            {"success":true,"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":100,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":120,"amount":100,"orders":2}]
            }}}
            """;
        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length()
            + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readHeaders(Socket socket) throws IOException {
        socket.setSoTimeout(2_000);
        var headers = new StringBuilder();
        try {
            while (!headers.toString().endsWith("\r\n\r\n")) {
                int next = socket.getInputStream().read();
                if (next < 0) {
                    break;
                }
                headers.append((char) next);
            }
        } catch (SocketException _) {
            // An aborted DNS request may connect and reset without sending any HTTP bytes.
        }
        return headers.toString();
    }

    private static void await(CountDownLatch latch) {
        try {
            Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Admission fixture interrupted", error);
        }
    }

    private static final class Reply extends SkyBlockBazaarReply implements SkyBlockBazaarReplyAccessor {
        // Plain JUnit does not apply the SDK timestamp accessor mixin.
        private final long receivedAt = System.currentTimeMillis();

        @Override
        public long getLastUpdated() {
            return this.receivedAt;
        }
    }

    private static final class BlockedDns implements DnsResolver {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            this.entered.countDown();
            boolean interrupted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (true) {
                try {
                    if (!this.release.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                        throw new UnknownHostException("Controlled DNS was not released");
                    }
                    break;
                } catch (InterruptedException _) {
                    // Model native DNS which cannot be forcibly stopped by Java interruption.
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        }
    }

    private static void assertClosed(Socket socket) throws IOException {
        socket.setSoTimeout(2_000);
        try {
            Assertions.assertEquals(-1, socket.getInputStream().read(), "HTTP abort must close the real socket");
        } catch (SocketException _) {
            // Apache abort can reset the peer connection instead of sending a graceful EOF.
        }
    }

    private static CompletableFuture<HypixelHttpResponse> requestWhenFree(
        BoundedBazaarHttpClient transport,
        String url
    ) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            try {
                return transport.makeRequest(url);
            } catch (RejectedExecutionException _) {
                Thread.sleep(10);
            }
        }
        throw new AssertionError("Aborted HTTP work still occupies its worker");
    }
}
