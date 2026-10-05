package com.github.lutzluca.coflnet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns public history requests, subscriber interests and bounded cached responses. */
public final class CoflnetClient implements AutoCloseable {
    private static final int BODY_LIMIT = 4 * 1024 * 1024;
    private static final int CACHE_WEIGHT_LIMIT = 16 * 1024 * 1024;
    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=\\\"?(\\d+)", Pattern.CASE_INSENSITIVE);
    private final URI base;
    private final Clock clock;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
        runnable -> new Thread(runnable, "coflnet-history"));
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
        runnable -> new Thread(runnable, "coflnet-deadline"));
    private boolean pumpScheduled;
    private final Map<URI, Job> inFlight = new HashMap<>();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<URI, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private Job active;
    private long nextRequestNanos;
    private int cacheWeight;
    private boolean closed;

    public CoflnetClient() {
        this(URI.create("https://sky.coflnet.com/api/"), Clock.systemUTC());
    }

    public CoflnetClient(URI base, Clock clock) {
        Objects.requireNonNull(base);
        if (!List.of("http", "https").contains(base.getScheme()) || base.getHost() == null
            || base.getQuery() != null
            || base.getFragment() != null) {
            throw new IllegalArgumentException("Expected an HTTP API base URI");
        }
        this.base = URI.create(base.toString().endsWith("/") ? base.toString() : base + "/");
        this.clock = Objects.requireNonNull(clock);
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    public CoflnetRequest<HistoryResponse> history(String productId, HistoryQuery query, boolean refresh) {
        if (productId == null || !productId.matches("[A-Za-z0-9_:-]{1,160}")) {
            throw new IllegalArgumentException("Invalid product ID");
        }
        Objects.requireNonNull(query);
        URI uri = this.base.resolve("bazaar/" + encode(productId) + "/history?start="
            + encode(query.start().toString()) + "&end=" + encode(query.end().toString()));
        return this.request(uri, true, refresh);
    }

    public CoflnetRequest<MayorResponse> mayors(Instant start, Instant end, boolean refresh) {
        new HistoryQuery(start, end);
        return this.request(this.base.resolve("mayor?from=" + encode(start.toString())
            + "&to=" + encode(end.toString())), false, refresh);
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> CoflnetRequest<T> request(URI uri, boolean history, boolean refresh) {
        Job job = this.inFlight.get(uri);
        if (job != null) {
            return this.subscribe(job);
        }
        CoflnetRequest<T> immediate = new CoflnetRequest<>(() -> {});
        if (this.closed) {
            immediate.fail(error(CoflnetException.Kind.CLOSED, uri, 0, "Client is closed", null));
            return immediate;
        }
        Entry entry = this.cache.get(uri);
        if (!refresh && entry != null && this.clock.instant().isBefore(entry.expires())) {
            immediate.succeed((T) entry.value());
            return immediate;
        }
        if (this.queue.size() >= 32) {
            immediate.fail(error(CoflnetException.Kind.QUEUE_FULL, uri, 0, "Request queue is full", null));
            return immediate;
        }
        job = new Job(uri, history);
        this.inFlight.put(uri, job);
        this.queue.add(job);
        CoflnetRequest<T> request = this.subscribe(job);
        this.schedulePump(0);
        return request;
    }

    private <T> CoflnetRequest<T> subscribe(Job job) {
        CoflnetRequest<T> request = new CoflnetRequest<>(() -> this.release(job));
        job.subscribers.add(request);
        return request;
    }

    private synchronized void release(Job job) {
        job.subscribers.removeIf(request -> request.completion().toCompletableFuture().isDone());
        if (!job.subscribers.isEmpty()) {
            return;
        }
        job.cancelled = true;
        this.inFlight.remove(job.uri, job);
        this.queue.remove(job);
        if (job.connection != null && !this.closed) {
            this.deadlines.execute(job.connection::disconnect);
        }
    }

    private synchronized void schedulePump(long delay) {
        if (this.closed || this.pumpScheduled) {
            return;
        }
        this.pumpScheduled = true;
        this.worker.schedule(this::pump, Math.max(0, delay), TimeUnit.NANOSECONDS);
    }

    private void pump() {
        Job job;
        synchronized (this) {
            this.pumpScheduled = false;
            if (this.closed || this.active != null || this.queue.isEmpty()) {
                return;
            }
            long wait = this.nextRequestNanos - System.nanoTime();
            if (wait > 0) {
                this.schedulePump(wait);
                return;
            }
            job = this.queue.remove();
            this.active = job;
            this.nextRequestNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(650);
        }
        Object result = null;
        Throwable failure = null;
        Instant expires = null;
        int weight = 0;
        boolean retry = false;
        ScheduledFuture<?> deadline = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) job.uri.toURL().openConnection();
            synchronized (this) {
                if (job.cancelled || this.closed) {
                    return;
                }
                job.connection = connection;
            }
            deadline = this.deadlines.schedule(connection::disconnect, 25, TimeUnit.SECONDS);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            Instant checked = this.clock.instant();
            if (status == 429 || status >= 500 && status <= 599) {
                long delay = retryDelay(connection.getHeaderField("Retry-After"), checked);
                synchronized (this) {
                    this.nextRequestNanos = Math.max(this.nextRequestNanos,
                        saturatingAdd(System.nanoTime(), delay));
                }
                retry = ++job.attempts < 3;
            }
            if (status != 200 && status != 204) {
                throw error(CoflnetException.Kind.HTTP, job.uri, status, "Coflnet HTTP " + status, null);
            }
            byte[] bytes = status == 204 ? new byte[0] : this.readBody(connection, job);
            weight = bytes.length * 4;
            result = this.parse(bytes, job.history, checked);
            if (result instanceof HistoryResponse response) {
                weight += response.points().size() * 256;
            }
            expires = expiry(connection, checked);
        } catch (CoflnetException exception) {
            failure = exception;
        } catch (IOException exception) {
            failure = error(CoflnetException.Kind.TRANSPORT, job.uri, 0, "Coflnet transport failure", exception);
            retry = ++job.attempts < 3;
        } catch (RuntimeException exception) {
            failure = error(CoflnetException.Kind.PARSING, job.uri, 0, "Invalid Coflnet response", exception);
        } finally {
            if (deadline != null) {
                deadline.cancel(false);
            }
            synchronized (this) {
                if (job.connection != null) {
                    job.connection.disconnect();
                    job.connection = null;
                }
                this.active = null;
                if (!job.cancelled && !this.closed) {
                    if (retry) {
                        this.queue.addFirst(job);
                    } else {
                        this.inFlight.remove(job.uri, job);
                        if (failure == null && result != null && expires != null) {
                            this.cache(job.uri, result, expires, weight);
                        }
                        for (CoflnetRequest<?> subscriber : List.copyOf(job.subscribers)) {
                            complete(subscriber, result, failure);
                        }
                        job.subscribers.clear();
                    }
                }
                this.schedulePump(0);
            }
        }
    }

    private byte[] readBody(HttpURLConnection connection, Job job) throws IOException {
        if (connection.getContentLengthLong() > BODY_LIMIT) {
            throw error(CoflnetException.Kind.BODY_LIMIT, job.uri, 200, "Response exceeds body limit", null);
        }
        try (
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (job.cancelled) {
                    throw new IOException("Request cancelled");
                }
                if (output.size() + count > BODY_LIMIT) {
                    throw error(CoflnetException.Kind.BODY_LIMIT, job.uri, 200, "Response exceeds body limit", null);
                }
                output.write(chunk, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void cache(URI uri, Object value, Instant expires, int weight) {
        Entry previous = this.cache.put(uri, new Entry(value, expires, weight));
        this.cacheWeight += weight - (previous == null ? 0 : previous.weight());
        while (this.cache.size() > 64 || this.cacheWeight > CACHE_WEIGHT_LIMIT) {
            URI oldest = this.cache.keySet().iterator().next();
            this.cacheWeight -= this.cache.remove(oldest).weight();
        }
    }

    private Object parse(byte[] bytes, boolean history, Instant checked) {
        JsonArray array = bytes.length == 0
            ? new JsonArray()
            : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray();
        if (history) {
            TreeMap<Instant, HistoryPoint> points = new TreeMap<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject row = element.getAsJsonObject();
                Instant timestamp = date(string(row, "timestamp"));
                if (timestamp == null) {
                    continue;
                }
                points.putIfAbsent(timestamp, new HistoryPoint(timestamp, number(row, "buy"), number(row, "sell"),
                    number(row, "minBuy"), number(row, "maxBuy"), number(row, "minSell"), number(row, "maxSell"),
                    integer(row, "buyVolume"), integer(row, "sellVolume"), integer(row, "buyMovingWeek"),
                    integer(row, "sellMovingWeek")));
            }
            return new HistoryResponse(List.copyOf(points.values()), checked,
                points.isEmpty() ? null : points.firstKey(), points.isEmpty() ? null : points.lastKey());
        }
        TreeMap<Instant, MayorTerm> terms = new TreeMap<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            Instant start = date(string(row, "start"));
            Instant end = date(string(row, "end"));
            if (start == null || end == null
                || !start.isBefore(end)
                || !row.has("winner")
                || !row.get("winner").isJsonObject()) {
                continue;
            }
            JsonObject winner = row.getAsJsonObject("winner");
            String name = string(winner, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            List<MayorPerk> perks = new ArrayList<>();
            if (winner.has("perks") && winner.get("perks").isJsonArray()) {
                for (JsonElement perk : winner.getAsJsonArray("perks")) {
                    if (perk.isJsonObject()) {
                        String perkName = string(perk.getAsJsonObject(), "name");
                        if (perkName != null && !perkName.isBlank()) {
                            perks.add(new MayorPerk(perkName, string(perk.getAsJsonObject(), "description")));
                        }
                    }
                }
            }
            terms.putIfAbsent(start, new MayorTerm(start, end, name, perks));
        }
        return new MayorResponse(List.copyOf(terms.values()), checked);
    }

    private static String string(JsonObject row, String name) {
        JsonElement value = row.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static Double number(JsonObject row, String name) {
        try {
            String value = string(row, name);
            Double number = value == null ? null : Double.valueOf(value);
            return number == null || !Double.isFinite(number) || number <= 0 ? null : number;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static Long integer(JsonObject row, String name) {
        try {
            String value = string(row, name);
            Long quantity = value == null ? null : Long.valueOf(value);
            return quantity == null || quantity < 0 ? null : quantity;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static Instant date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException _) {
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException _) {
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("M/d/uuuu HH:mm:ss XXX", Locale.ROOT))
                .toInstant();
        } catch (RuntimeException _) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("M/d/uuuu HH:mm:ss", Locale.ROOT))
                .toInstant(ZoneOffset.UTC);
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static Instant expiry(HttpURLConnection connection, Instant checked) {
        String control = Objects.toString(connection.getHeaderField("Cache-Control"), "");
        if (control.toLowerCase(Locale.ROOT).contains("no-store")) {
            return null;
        }
        if (control.toLowerCase(Locale.ROOT).contains("no-cache")) {
            return checked;
        }
        Matcher matcher = MAX_AGE.matcher(control);
        long ttl = matcher.find() ? parseSeconds(matcher.group(1)) : 60;
        long age = parseSeconds(connection.getHeaderField("Age"));
        return checked.plusSeconds(Math.min(86400, Math.max(0, ttl - age)));
    }

    private static long retryDelay(String header, Instant checked) {
        if (header == null) {
            return TimeUnit.SECONDS.toNanos(2);
        }
        try {
            return TimeUnit.SECONDS.toNanos(Math.max(0, Long.parseLong(header.trim())));
        } catch (NumberFormatException _) {
            try {
                long seconds = Duration
                    .between(checked, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                    .getSeconds();
                return TimeUnit.SECONDS.toNanos(Math.max(0, seconds));
            } catch (RuntimeException _) {
                return TimeUnit.SECONDS.toNanos(2);
            }
        }
    }

    private static long parseSeconds(String value) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (RuntimeException _) {
            return 0;
        }
    }

    private static long saturatingAdd(long value, long delta) {
        try {
            return Math.addExact(value, delta);
        } catch (ArithmeticException _) {
            return Long.MAX_VALUE;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static CoflnetException error(
        CoflnetException.Kind kind,
        URI uri,
        int status,
        String message,
        Throwable cause
    ) {
        return new CoflnetException(kind, uri, status, message, cause);
    }

    @SuppressWarnings("unchecked")
    private static <T> void complete(CoflnetRequest<T> request, Object result, Throwable failure) {
        if (failure != null) {
            request.fail(failure);
        } else {
            request.succeed((T) result);
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.active != null && this.active.connection != null) {
            this.active.connection.disconnect();
        }
        for (Job job : List.copyOf(this.inFlight.values())) {
            job.cancelled = true;
            if (job.connection != null) {
                job.connection.disconnect();
            }
            for (CoflnetRequest<?> request : List.copyOf(job.subscribers)) {
                request.fail(error(CoflnetException.Kind.CLOSED, job.uri, 0, "Client is closed", null));
            }
        }
        this.inFlight.clear();
        this.queue.clear();
        this.cache.clear();
        this.cacheWeight = 0;
        this.worker.shutdownNow();
        this.deadlines.shutdownNow();
    }

    private record Entry(Object value, Instant expires, int weight) {}

    private static final class Job {
        private final URI uri;
        private final boolean history;
        private final List<CoflnetRequest<?>> subscribers = new ArrayList<>();
        private volatile boolean cancelled;
        private HttpURLConnection connection;
        private int attempts;

        private Job(URI uri, boolean history) {
            this.uri = uri;
            this.history = history;
        }
    }
}
