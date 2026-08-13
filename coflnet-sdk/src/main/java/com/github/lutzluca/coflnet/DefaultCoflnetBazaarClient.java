package com.github.lutzluca.coflnet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DefaultCoflnetBazaarClient implements CoflnetBazaarClient {
    private static final Pattern ITEM_TAG = Pattern.compile("[A-Za-z0-9_:-]{1,160}");
    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*\\\"?(\\d+)\\\"?", Pattern.CASE_INSENSITIVE);
    private static final Type HISTORY_LIST = new TypeToken<List<BazaarHistoryPoint>>() { }.getType();
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_ERROR_BODY_LENGTH = 4_096;

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ExecutorService requestExecutor;
    private final Gson gson;
    private final Map<String, String> requestHeaders;
    private final DualWindowRateLimiter rateLimiter = new DualWindowRateLimiter();
    private final Object stateLock = new Object();
    private final Map<RequestKey, CacheEntry> cache = new HashMap<>();
    private final Map<RequestKey, CompletableFuture<Object>> inFlight = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    static CoflnetBazaarClient create(URI baseUri) {
        return create(baseUri, Map.of());
    }

    // Kept below the public surface so bearer-token support can be added without changing callers.
    static CoflnetBazaarClient create(URI baseUri, Map<String, String> requestHeaders) {
        URI normalizedBaseUri = normalizeBaseUri(baseUri);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coflnet-bazaar-client");
            thread.setDaemon(true);
            return thread;
        });
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new DefaultCoflnetBazaarClient(normalizedBaseUri, httpClient, executor, requestHeaders);
    }

    DefaultCoflnetBazaarClient(
            URI baseUri,
            HttpClient httpClient,
            ExecutorService requestExecutor,
            Map<String, String> requestHeaders
    ) {
        this.baseUri = normalizeBaseUri(baseUri);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestExecutor = Objects.requireNonNull(requestExecutor, "requestExecutor");
        this.requestHeaders = Map.copyOf(requestHeaders);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new CoflnetInstantAdapter())
                .create();
    }

    @Override
    public CompletionStage<Optional<BazaarSnapshot>> snapshot(String itemTag) {
        String validTag = validateItemTag(itemTag);
        RequestSpec request = new RequestSpec(
                new RequestKey(baseUri.resolve("bazaar/" + validTag + "/snapshot"), ResponseKind.SNAPSHOT),
                360
        );
        return load(request).thenApply(value -> cast(value, Optional.class));
    }

    @Override
    public CompletionStage<List<BazaarHistoryPoint>> history(String itemTag, HistoryRange range) {
        String validTag = validateItemTag(itemTag);
        Objects.requireNonNull(range, "range");

        String relativePath = "bazaar/" + validTag + "/history";
        long fallbackMaxAge;
        if (range instanceof HistoryRange.Preset preset) {
            relativePath += "/" + preset.name().toLowerCase(Locale.ROOT);
            fallbackMaxAge = switch (preset) {
                case HOUR -> 300;
                case DAY -> 600;
                case WEEK -> 3_600;
            };
        } else if (range instanceof HistoryRange.Custom custom) {
            relativePath += "?start=" + encode(custom.start().toString())
                    + "&end=" + encode(custom.end().toString());
            fallbackMaxAge = 3_600;
        } else {
            throw new IllegalArgumentException("Unsupported history range: " + range.getClass().getName());
        }

        RequestSpec request = new RequestSpec(
                new RequestKey(baseUri.resolve(relativePath), ResponseKind.HISTORY),
                fallbackMaxAge
        );
        return load(request).thenApply(value -> cast(value, List.class));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        CoflnetApiException closeFailure = new CoflnetApiException(
                "Coflnet client is closed", -1, baseUri, null
        );
        synchronized (stateLock) {
            // Serialize shutdown with load(), which submits work while holding this lock.
            // This prevents an accepted load from racing into a rejected executor submission.
            requestExecutor.shutdownNow();
            inFlight.values().forEach(future -> future.completeExceptionally(closeFailure));
            inFlight.clear();
            cache.clear();
        }
    }

    private CompletableFuture<Object> load(RequestSpec request) {
        synchronized (stateLock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new CoflnetApiException(
                        "Coflnet client is closed", -1, request.key().uri(), null
                ));
            }

            CacheEntry cached = cache.get(request.key());
            long now = System.nanoTime();
            if (cached != null) {
                if (cached.expiresAtNanos() > now) {
                    return CompletableFuture.completedFuture(cached.value());
                }
                cache.remove(request.key());
            }

            CompletableFuture<Object> existing = inFlight.get(request.key());
            if (existing != null) {
                return existing;
            }

            CompletableFuture<Object> created = new CompletableFuture<>();
            inFlight.put(request.key(), created);
            requestExecutor.execute(() -> executeAndComplete(request, created));
            return created;
        }
    }

    private void executeAndComplete(RequestSpec request, CompletableFuture<Object> future) {
        try {
            DecodedResponse decoded = executeWithRetries(request);
            synchronized (stateLock) {
                if (decoded.maxAgeSeconds() > 0 && !closed.get()) {
                    long ttlNanos = TimeUnit.SECONDS.toNanos(decoded.maxAgeSeconds());
                    cache.put(request.key(), new CacheEntry(decoded.value(), saturatingAdd(System.nanoTime(), ttlNanos)));
                }
                inFlight.remove(request.key(), future);
            }
            future.complete(decoded.value());
        } catch (Throwable failure) {
            synchronized (stateLock) {
                inFlight.remove(request.key(), future);
            }
            future.completeExceptionally(failure);
        }
    }

    private DecodedResponse executeWithRetries(RequestSpec request) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(request.key().uri())
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "BtrBz-Coflnet-SDK/1")
                .GET();
        requestHeaders.forEach(requestBuilder::header);
        HttpRequest httpRequest = requestBuilder.build();

        IOException lastIoFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ensureOpen(request.key().uri());
            rateLimiter.acquire();

            HttpResponse<String> response;
            try {
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CoflnetApiException(
                        "Coflnet request was interrupted", -1, request.key().uri(), null, interrupted
                );
            } catch (IOException ioFailure) {
                lastIoFailure = ioFailure;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleep(backoff(attempt), request.key().uri());
                continue;
            }

            rateLimiter.observe(response);
            int statusCode = response.statusCode();
            if (statusCode == 200 || statusCode == 204) {
                Object value = decodeSuccess(request.key(), statusCode, response.body());
                long maxAge = maxAgeSeconds(response).orElse(request.fallbackMaxAgeSeconds());
                return new DecodedResponse(value, maxAge);
            }

            if (statusCode == 429 && attempt < MAX_ATTEMPTS) {
                Duration retryDelay = retryAfter(response).orElse(backoff(attempt));
                rateLimiter.defer(retryDelay);
                sleep(retryDelay, request.key().uri());
                continue;
            }

            if (statusCode >= 500 && statusCode <= 599 && attempt < MAX_ATTEMPTS) {
                sleep(backoff(attempt), request.key().uri());
                continue;
            }

            throw apiFailure(request.key().uri(), response);
        }

        throw new CoflnetApiException(
                "Coflnet request failed after " + MAX_ATTEMPTS + " attempts",
                -1,
                request.key().uri(),
                null,
                lastIoFailure
        );
    }

    private Object decodeSuccess(RequestKey key, int statusCode, String body) {
        if (statusCode == 204) {
            return key.responseKind() == ResponseKind.SNAPSHOT ? Optional.empty() : List.of();
        }

        try {
            return switch (key.responseKind()) {
                case SNAPSHOT -> {
                    BazaarSnapshot snapshot = gson.fromJson(body, BazaarSnapshot.class);
                    if (snapshot == null) {
                        throw new IllegalStateException("Snapshot response was JSON null");
                    }
                    yield Optional.of(snapshot);
                }
                case HISTORY -> {
                    List<BazaarHistoryPoint> points = gson.fromJson(body, HISTORY_LIST);
                    if (points == null) {
                        throw new IllegalStateException("History response was JSON null");
                    }
                    yield List.copyOf(points);
                }
            };
        } catch (RuntimeException malformed) {
            throw new CoflnetApiException(
                    "Coflnet returned malformed JSON", statusCode, key.uri(), truncate(body), malformed
            );
        }
    }

    private static CoflnetApiException apiFailure(URI uri, HttpResponse<String> response) {
        return new CoflnetApiException(
                "Coflnet API returned HTTP " + response.statusCode(),
                response.statusCode(),
                uri,
                truncate(response.body())
        );
    }

    private void ensureOpen(URI uri) {
        if (closed.get()) {
            throw new CoflnetApiException("Coflnet client is closed", -1, uri, null);
        }
    }

    private static Optional<Long> maxAgeSeconds(HttpResponse<?> response) {
        Optional<String> cacheControl = response.headers().firstValue("Cache-Control");
        if (cacheControl.isEmpty()) {
            return Optional.empty();
        }
        String value = cacheControl.get();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("no-store") || normalized.contains("no-cache")) {
            return Optional.of(0L);
        }
        Matcher matcher = MAX_AGE.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            long maxAge = Long.parseLong(matcher.group(1));
            long responseAge = response.headers()
                    .firstValue("Age")
                    .flatMap(DefaultCoflnetBazaarClient::nonNegativeLong)
                    .orElse(0L);
            return Optional.of(Math.max(0, maxAge - responseAge));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Long> nonNegativeLong(String value) {
        try {
            return Optional.of(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isEmpty()) {
            return Optional.empty();
        }
        String value = header.get().trim();
        try {
            long seconds = Long.parseLong(value);
            return Optional.of(Duration.ofSeconds(Math.max(0, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration remaining = Duration.between(Instant.now(), retryAt);
                return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
            } catch (DateTimeParseException invalid) {
                return Optional.empty();
            }
        }
    }

    private static Duration backoff(int attempt) {
        long baseMillis = Math.min(2_000, 250L << Math.min(attempt - 1, 3));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, baseMillis / 5));
        return Duration.ofMillis(baseMillis + jitter);
    }

    private static void sleep(Duration duration, URI uri) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(duration.toNanos());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CoflnetApiException("Coflnet retry wait was interrupted", -1, uri, null, interrupted);
        }
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");
        String scheme = baseUri.getScheme();
        if (!baseUri.isAbsolute() || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUri must not contain a query or fragment");
        }
        String value = baseUri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static String validateItemTag(String itemTag) {
        Objects.requireNonNull(itemTag, "itemTag");
        if (!ITEM_TAG.matcher(itemTag).matches()) {
            throw new IllegalArgumentException("itemTag contains unsupported characters");
        }
        return itemTag;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String truncate(String body) {
        if (body == null || body.length() <= MAX_ERROR_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_LENGTH);
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value, Class<?> ignoredTypeToken) {
        return (T) value;
    }

    private enum ResponseKind {
        SNAPSHOT,
        HISTORY
    }

    private record RequestKey(URI uri, ResponseKind responseKind) {
    }

    private record RequestSpec(RequestKey key, long fallbackMaxAgeSeconds) {
    }

    private record CacheEntry(Object value, long expiresAtNanos) {
    }

    private record DecodedResponse(Object value, long maxAgeSeconds) {
    }

    private static final class DualWindowRateLimiter {
        private static final int BURST_LIMIT = 30;
        private static final int MINUTE_LIMIT = 100;
        private static final long BURST_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();
        private static final long MINUTE_WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

        private final ArrayDeque<Long> burstStarts = new ArrayDeque<>();
        private final ArrayDeque<Long> minuteStarts = new ArrayDeque<>();
        private volatile long serverNotBeforeEpochMillis;

        void acquire() {
            while (true) {
                long now = System.nanoTime();
                prune(burstStarts, now - BURST_WINDOW_NANOS);
                prune(minuteStarts, now - MINUTE_WINDOW_NANOS);

                long localWait = Math.max(
                        requiredWait(burstStarts, BURST_LIMIT, BURST_WINDOW_NANOS, now),
                        requiredWait(minuteStarts, MINUTE_LIMIT, MINUTE_WINDOW_NANOS, now)
                );
                long serverWaitMillis = Math.max(0, serverNotBeforeEpochMillis - System.currentTimeMillis());
                long waitNanos = Math.max(localWait, TimeUnit.MILLISECONDS.toNanos(serverWaitMillis));
                if (waitNanos <= 0) {
                    long admittedAt = System.nanoTime();
                    burstStarts.addLast(admittedAt);
                    minuteStarts.addLast(admittedAt);
                    return;
                }

                try {
                    TimeUnit.NANOSECONDS.sleep(waitNanos);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CoflnetApiException(
                            "Coflnet rate-limit wait was interrupted", -1, CoflnetBazaarClient.DEFAULT_BASE_URI, null,
                            interrupted
                    );
                }
            }
        }

        void observe(HttpResponse<?> response) {
            Optional<String> remainingHeader = response.headers().firstValue("x-rate-limit-remaining");
            Optional<String> resetHeader = response.headers().firstValue("x-rate-limit-reset");
            if (remainingHeader.isEmpty() || resetHeader.isEmpty()) {
                return;
            }
            try {
                long remaining = Long.parseLong(remainingHeader.get());
                if (remaining <= 0) {
                    Instant reset = Instant.parse(resetHeader.get());
                    serverNotBeforeEpochMillis = Math.max(serverNotBeforeEpochMillis, reset.toEpochMilli());
                }
            } catch (NumberFormatException | DateTimeParseException ignored) {
                // Headers are advisory; the local dual-window limiter remains authoritative.
            }
        }

        void defer(Duration duration) {
            long delayMillis;
            try {
                delayMillis = duration.toMillis();
            } catch (ArithmeticException overflow) {
                delayMillis = Long.MAX_VALUE;
            }
            long target = saturatingAdd(System.currentTimeMillis(), Math.max(0, delayMillis));
            serverNotBeforeEpochMillis = Math.max(serverNotBeforeEpochMillis, target);
        }

        private static void prune(ArrayDeque<Long> starts, long cutoff) {
            while (!starts.isEmpty() && starts.peekFirst() <= cutoff) {
                starts.removeFirst();
            }
        }

        private static long requiredWait(ArrayDeque<Long> starts, int limit, long window, long now) {
            if (starts.size() < limit) {
                return 0;
            }
            return Math.max(0, starts.peekFirst() + window - now);
        }
    }
}
