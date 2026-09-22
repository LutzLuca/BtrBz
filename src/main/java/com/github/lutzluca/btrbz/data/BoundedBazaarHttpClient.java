package com.github.lutzluca.btrbz.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/** A bounded transport for the SDK's keyless Bazaar request. The SDK still owns decoding. */
@Slf4j
final class BoundedBazaarHttpClient implements HypixelHttpClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 15_000;
    private static final int TOTAL_TIMEOUT_MS = 25_000;

    private final CloseableHttpClient client;
    private final ExecutorService requests;
    private final ScheduledExecutorService deadlines;
    private final int totalTimeoutMs;
    private final AtomicReference<HttpGet> activeRequest = new AtomicReference<>();

    BoundedBazaarHttpClient() {
        this(CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, TOTAL_TIMEOUT_MS);
    }

    BoundedBazaarHttpClient(int connectTimeoutMs, int socketTimeoutMs, int totalTimeoutMs) {
        if (connectTimeoutMs <= 0 || socketTimeoutMs <= 0 || totalTimeoutMs <= 0) {
            throw new IllegalArgumentException("Bazaar HTTP timeouts must be positive");
        }
        this.totalTimeoutMs = totalTimeoutMs;
        var config = RequestConfig.custom()
            .setConnectTimeout(connectTimeoutMs)
            .setConnectionRequestTimeout(connectTimeoutMs)
            .setSocketTimeout(socketTimeoutMs)
            .build();
        this.client = HttpClientBuilder.create()
            .setUserAgent(DEFAULT_USER_AGENT)
            .setDefaultRequestConfig(config)
            .build();
        this.requests = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(), task -> {
                var thread = new Thread(task, "bazaar-http");
                thread.setDaemon(true);
                return thread;
            });
        this.deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "bazaar-http-deadline");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<HypixelHttpResponse> makeRequest(String url) {
        var request = new HttpGet(url);
        if (!this.activeRequest.compareAndSet(null, request)) {
            throw new RejectedExecutionException("Bazaar HTTP request already active");
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                ScheduledFuture<?> deadline = null;
                try {
                    deadline = this.deadlines.schedule(request::abort, this.totalTimeoutMs, TimeUnit.MILLISECONDS);
                    try (var response = this.client.execute(request)) {
                        var entity = response.getEntity();
                        String body = entity == null ? "" : EntityUtils.toString(entity, "UTF-8");
                        return new HypixelHttpResponse(response.getStatusLine().getStatusCode(), body);
                    }
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                } finally {
                    if (deadline != null) {
                        deadline.cancel(false);
                    }
                    request.releaseConnection();
                    this.activeRequest.compareAndSet(request, null);
                }
            }, this.requests);
        } catch (RuntimeException error) {
            this.activeRequest.compareAndSet(request, null);
            throw error;
        }
    }

    void abortCurrentRequest() {
        var current = this.activeRequest.get();
        if (current != null) {
            current.abort();
        }
    }

    @Override
    public CompletableFuture<HypixelHttpResponse> makeAuthenticatedRequest(String url) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "Bazaar polling only uses the keyless Hypixel endpoint"));
    }

    @Override
    public void shutdown() {
        this.abortCurrentRequest();
        this.deadlines.shutdownNow();
        try {
            this.client.close();
        } catch (IOException error) {
            log.warn("Could not close Bazaar HTTP client", error);
        } finally {
            this.requests.shutdownNow();
        }
    }
}
