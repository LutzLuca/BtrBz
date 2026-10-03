package com.github.lutzluca.btrbz.data;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.util.EntityUtils;

/** Owns the actual HTTP operation; cancelling an SDK decoding stage cannot release its capacity. */
@Slf4j
final class BoundedBazaarHttpClient implements HypixelHttpClient {
    private final CloseableHttpClient client;
    private final ThreadPoolExecutor worker;
    private final ScheduledExecutorService deadlines;
    private final int totalTimeoutMs;
    private final AtomicReference<HttpGet> activeRequest = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    BoundedBazaarHttpClient() {
        this(5_000, 15_000, 25_000);
    }

    BoundedBazaarHttpClient(int connectTimeoutMs, int socketTimeoutMs, int totalTimeoutMs) {
        this(connectTimeoutMs, socketTimeoutMs, totalTimeoutMs, SystemDefaultDnsResolver.INSTANCE);
    }

    BoundedBazaarHttpClient(int connectTimeoutMs, int socketTimeoutMs, int totalTimeoutMs, DnsResolver dnsResolver) {
        if (connectTimeoutMs <= 0 || socketTimeoutMs <= 0 || totalTimeoutMs <= 0) {
            throw new IllegalArgumentException("Bazaar HTTP deadlines must be positive");
        }
        this.totalTimeoutMs = totalTimeoutMs;
        this.client = HttpClients.custom()
            .setDnsResolver(dnsResolver)
            .setUserAgent(DEFAULT_USER_AGENT)
            .disableAutomaticRetries()
            .setMaxConnTotal(1)
            .setMaxConnPerRoute(1)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .build())
            .build();
        this.worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
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
        if (this.closed.get() || !this.activeRequest.compareAndSet(null, request)) {
            throw new RejectedExecutionException("Bazaar HTTP transport is closed or busy");
        }
        var result = new CompletableFuture<HypixelHttpResponse>();
        try {
            if (this.closed.get()) {
                throw new RejectedExecutionException("Bazaar HTTP transport is closed");
            }
            // Always run cleanup, even if the raw future was cancelled before this worker starts.
            this.worker.execute(() -> this.performRequest(request, result));
        } catch (RuntimeException error) {
            request.abort();
            this.activeRequest.compareAndSet(request, null);
            throw error;
        }
        return result;
    }

    private void performRequest(HttpGet request, CompletableFuture<HypixelHttpResponse> result) {
        ScheduledFuture<?> deadline = null;
        try {
            deadline = this.deadlines.schedule(() -> {
                // Native DNS may ignore abort. Report the deadline while retaining the only physical worker.
                result.completeExceptionally(new TimeoutException("Bazaar HTTP request exceeded its total deadline"));
                request.abort();
            }, this.totalTimeoutMs, TimeUnit.MILLISECONDS);
            try (var response = this.client.execute(request)) {
                var entity = response.getEntity();
                String body = entity == null ? "" : EntityUtils.toString(entity, "UTF-8");
                result.complete(new HypixelHttpResponse(response.getStatusLine().getStatusCode(), body));
            }
        } catch (Exception error) {
            result.completeExceptionally(error);
        } finally {
            if (deadline != null) {
                deadline.cancel(false);
            }
            request.releaseConnection();
            this.activeRequest.compareAndSet(request, null);
        }
    }

    void abortCurrentRequest() {
        var request = this.activeRequest.get();
        if (request != null) {
            request.abort();
        }
    }

    @Override
    public CompletableFuture<HypixelHttpResponse> makeAuthenticatedRequest(String url) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Bazaar requests are keyless"));
    }

    @Override
    public void shutdown() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.abortCurrentRequest();
        this.deadlines.shutdownNow();
        try {
            this.client.close();
        } catch (IOException error) {
            log.warn("Could not close Bazaar HTTP transport", error);
        } finally {
            this.worker.shutdownNow();
        }
    }
}
