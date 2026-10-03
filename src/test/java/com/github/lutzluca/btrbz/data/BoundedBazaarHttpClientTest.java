package com.github.lutzluca.btrbz.data;

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
                var composed = raw.thenApply(_ -> new BazaarPollerTest.Reply(System.currentTimeMillis()));
                Assertions.assertTrue(dns.entered.await(2, TimeUnit.SECONDS));
                var failure = Assertions.assertThrows(ExecutionException.class, () -> raw.get(2, TimeUnit.SECONDS));
                Assertions.assertInstanceOf(TimeoutException.class, failure.getCause());
                Assertions.assertThrows(ExecutionException.class, () -> composed.get(2, TimeUnit.SECONDS));
                Assertions.assertThrows(RejectedExecutionException.class, () -> transport.makeRequest(url(server)));

                var received = responder.submit(() -> {
                    while (true) {
                        try (var socket = server.accept()) {
                            String headers = readHeaders(socket);
                            if (!headers.isEmpty()) {
                                respond(socket);
                                return headers;
                            }
                        }
                    }
                });
                dns.release.countDown();
                var recovery = requestWhenFree(transport, url(server).replace("/bazaar", "/recovered"));
                Assertions.assertEquals("ok", recovery.get(2, TimeUnit.SECONDS).getBody());
                Assertions.assertTrue(received.get(2, TimeUnit.SECONDS).startsWith("GET /recovered "),
                    "The expired request must not send HTTP after DNS returns");
            } finally {
                dns.release.countDown();
                transport.shutdown();
                responder.shutdownNow();
                Assertions.assertTrue(responder.awaitTermination(3, TimeUnit.SECONDS));
            }
            assertTransportThreadsStop();
        }
    }

    @Test
    void closeRetainsUninterruptibleDnsWorkerUntilNativeWorkReturns() throws Exception {
        try (var server = server()) {
            var dns = new BlockedDns();
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 200, dns);
            try {
                var raw = transport.makeRequest("http://bazaar.test:" + server.getLocalPort() + "/expired");
                Assertions.assertTrue(dns.entered.await(2, TimeUnit.SECONDS));
                Assertions.assertThrows(ExecutionException.class, () -> raw.get(2, TimeUnit.SECONDS));
                transport.shutdown();
                Assertions.assertTrue(Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive() && thread.getName().equals("bazaar-http")));
                Assertions.assertThrows(RejectedExecutionException.class, () -> transport.makeRequest(url(server)));
                dns.release.countDown();
                assertTransportThreadsStop();
                server.setSoTimeout(150);
                Assertions.assertThrows(SocketTimeoutException.class, server::accept);
            } finally {
                dns.release.countDown();
                transport.shutdown();
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
                            .thenApply(_ -> new BazaarPollerTest.Reply(System.currentTimeMillis()));
                        staleStage.complete(stage);
                        await(returnStage);
                        return stage;
                    }
                    return transport.makeRequest(url(server))
                        .thenApply(_ -> new BazaarPollerTest.Reply(System.currentTimeMillis()));
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
                Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
                transport.shutdown();
            }
            assertTransportThreadsStop();
        }
    }

    @Test
    void repeatedDeadlinesCloseRealSocketsAndReleaseCapacityForRecovery() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 350);
            try {
                String url = url(server);
                for (int attempt = 0; attempt < 3; attempt++) {
                    var result = requestWhenFree(transport, url);
                    try (var socket = server.accept()) {
                        readRequest(socket);
                        Assertions.assertThrows(RejectedExecutionException.class, () -> transport.makeRequest(url));
                        Assertions.assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
                        assertClosed(socket);
                    }
                }
                var recovered = requestWhenFree(transport, url);
                try (var socket = server.accept()) {
                    readRequest(socket);
                    respond(socket);
                    Assertions.assertEquals("ok", recovered.get(3, TimeUnit.SECONDS).getBody());
                }
            } finally {
                transport.shutdown();
            }
            assertTransportThreadsStop();
        }
    }

    @Test
    void socketInactivityBoundClosesTheActualConnection() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 200, 3_000);
            try {
                var result = transport.makeRequest(url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    Assertions.assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
                    assertClosed(socket);
                }
            } finally {
                transport.shutdown();
            }
        }
    }

    @Test
    void slowBodyTrickleStillHitsTheTotalDeadline() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 300, 500);
            var writer = Executors.newSingleThreadExecutor();
            try {
                long started = System.nanoTime();
                var result = transport.makeRequest(url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    var trickle = writer.submit(() -> {
                        try {
                            for (int count = 0; count < 100; count++) {
                                socket.getOutputStream().write('x');
                                socket.getOutputStream().flush();
                                Thread.sleep(30);
                            }
                        } catch (IOException _) {
                            // A closed client socket is expected after the total deadline.
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    Assertions.assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
                    Assertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000);
                    assertClosed(socket);
                    trickle.cancel(true);
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
                    socket.setSoTimeout(2_000);
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
    void cancellingRawFutureAlsoKeepsThePhysicalWorkerUntilAbort() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 3_000, 3_000);
            try {
                var result = transport.makeRequest(url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    result.cancel(true);
                    Assertions.assertThrows(RejectedExecutionException.class,
                        () -> transport.makeRequest(url(server)));
                    transport.abortCurrentRequest();
                    assertClosed(socket);
                }
                var next = requestWhenFree(transport, url(server));
                try (var socket = server.accept()) {
                    readRequest(socket);
                    respond(socket);
                    Assertions.assertEquals("ok", next.get(3, TimeUnit.SECONDS).getBody());
                }
            } finally {
                transport.shutdown();
            }
        }
    }

    @Test
    void pollerStopRestartAndCloseAbortSdkRequestsAndPreserveOnePhysicalRequest() throws Exception {
        try (var server = server()) {
            var transport = new BoundedBazaarHttpClient(1_000, 4_000, 4_000);
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var recovered = new CompletableFuture<BazaarPoller.MarketReply>();
            var poller = new BazaarPoller(recovered::complete, () -> {}, () -> {}, () -> {},
                localApi(transport, url(server)), scheduler, Runnable::run, System::currentTimeMillis,
                transport::abortCurrentRequest);
            try {
                poller.start();
                try (var oldSocket = server.accept()) {
                    readRequest(oldSocket);
                    poller.stop();
                    poller.start();
                    assertClosed(oldSocket);
                    try (var nextSocket = server.accept()) {
                        readRequest(nextSocket);
                        Assertions.assertThrows(RejectedExecutionException.class,
                            () -> transport.makeRequest(url(server)));
                        respond(nextSocket);
                        Assertions.assertTrue(recovered.get(3, TimeUnit.SECONDS).snapshot().available());
                    }
                }
                poller.stop();
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
                Assertions.assertTrue(scheduler.awaitTermination(3, TimeUnit.SECONDS));
                transport.shutdown();
            }
            assertTransportThreadsStop();
        }
    }

    private static HypixelAPI localApi(BoundedBazaarHttpClient transport, String url) {
        return new HypixelAPI(transport) {
            @Override
            public CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaar() {
                // Preserve the SDK's composed-future ownership; endpoint decoding is its own contract.
                return transport.makeRequest(url)
                    .thenApply(_ -> new BazaarPollerTest.Reply(System.currentTimeMillis()));
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
        socket.setSoTimeout(2_000);
        int matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while (matched < end.length) {
            int next = socket.getInputStream().read();
            Assertions.assertNotEquals(-1, next, "Request ended before HTTP headers");
            matched = next == end[matched] ? matched + 1 : next == end[0] ? 1 : 0;
        }
    }

    private static void respond(Socket socket) throws IOException {
        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
            .getBytes(StandardCharsets.US_ASCII));
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

    static final class BlockedDns implements DnsResolver {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            this.entered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    this.release.await();
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

    private static void assertTransportThreadsStop() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            boolean running = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && (thread.getName().equals("bazaar-http")
                    || thread.getName().equals("bazaar-http-deadline")));
            if (!running) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Closed transport retained a worker or deadline thread");
    }
}
